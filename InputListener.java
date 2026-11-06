package pl.buffweapons;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.action.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dołączanie, aktywacja ultimate klawiszami, doskok włóczni, ładowanie topora. */
public final class InputListener implements Listener {
    private final BuffWeapons plugin;
    private final Map<UUID, Integer> lastDash = new HashMap<>();

    public InputListener(BuffWeapons plugin) {
        this.plugin = plugin;
    }

    private WeaponType heldType(Player p) {
        return WeaponType.of(p.getInventory().getItemInMainHand().getType());
    }

    // --------------------------------------------------- dołączanie / wyjście

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // małe opóźnienie, żeby tytuł nie zginął w ekranie ładowania
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.reroll().ensureWeapon(p);
            plugin.reroll().discover(p);
            plugin.ultimates().restoreInventory(p); // ewentualne resztki po crashu
            plugin.attributes().update(p);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.ultimates().onQuit(p);
        plugin.attributes().clear(p);
        lastDash.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        plugin.ultimates().end(p, false);
        plugin.ultimates().resetProgress(p);
        for (ItemStack drop : e.getDrops()) {
            plugin.ultimates().restoreCrossbow(drop);
        }
    }

    // --------------------------------------------------------- aktywacja

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        WeaponType assigned = plugin.data().weapon(p);
        if (assigned == null || heldType(p) != assigned) return;
        if (!plugin.data().methodEnabled(p, ActivationMethod.SHIFT_F)) return;
        e.setCancelled(true);
        plugin.ultimates().tryActivate(p, ActivationMethod.SHIFT_F);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();
        WeaponType assigned = plugin.data().weapon(p);

        if (p.isSneaking() && assigned != null
                && WeaponType.of(dropped.getType()) == assigned
                && plugin.data().methodEnabled(p, ActivationMethod.SHIFT_Q)) {
            e.setCancelled(true);
            plugin.ultimates().tryActivate(p, ActivationMethod.SHIFT_Q);
            return;
        }
        // wyrzucona kusza nie zostaje z Quick Charge V
        if (plugin.ultimates().restoreCrossbow(dropped)) {
            e.getItemDrop().setItemStack(dropped);
        }
    }

    // ------------------------------------------------- PPM, LPM, doskok

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Action a = e.getAction();
        if (a.isRightClick()) {
            plugin.ultimates().noteUseClick(p);
            dash(p);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            plugin.ultimates().breakAxe(p, "zamachnąłeś się");
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) dash(e.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player p = e.getPlayer();
        if (plugin.ultimates().isCharging(p)) {
            plugin.ultimates().breakAxe(p, "ruszyłeś się");
        }
    }

    /** Ultimate włóczni: doskok jak Lunge III, przy ataku i przy użyciu PPM. */
    private void dash(Player p) {
        if (!plugin.ultimates().isActive(p, WeaponType.SPEAR) || heldType(p) != WeaponType.SPEAR) return;
        var cfg = plugin.getConfig();
        int now = Bukkit.getCurrentTick();
        Integer last = lastDash.get(p.getUniqueId());
        if (last != null && now - last < cfg.getInt("spear.ultimate.dash-cooldown-ticks", 12)) return;
        lastDash.put(p.getUniqueId(), now);

        Vector dir = p.getEyeLocation().getDirection().clone();
        dir.setY(Math.max(-0.35, Math.min(0.5, dir.getY())));
        if (dir.lengthSquared() < 1e-4) return;
        dir.normalize().multiply(cfg.getDouble("spear.ultimate.dash-power", 1.6));
        if (p.isOnGround()) dir.setY(Math.max(dir.getY(), 0.25));
        p.setVelocity(dir);

        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.3f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 10, 0.3, 0.3, 0.3, 0.05);
    }
}
