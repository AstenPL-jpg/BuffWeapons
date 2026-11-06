package pl.buffweapons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Zamrożenie od buffa topora: gracz nie może nic zrobić, moby tracą AI. */
public final class FreezeManager implements Listener {
    private final BuffWeapons plugin;
    private final Map<UUID, Integer> until = new HashMap<>();
    private final Map<UUID, Mob> frozenMobs = new HashMap<>();

    public FreezeManager(BuffWeapons plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(Entity e) {
        Integer end = until.get(e.getUniqueId());
        return end != null && end > Bukkit.getCurrentTick();
    }

    public void freeze(LivingEntity e, int ticks) {
        UUID id = e.getUniqueId();
        if (isFrozen(e)) return;
        until.put(id, Bukkit.getCurrentTick() + ticks);

        if (e instanceof Player p) {
            p.sendActionBar(Text.c("<aqua>Jesteś zamrożony!"));
        } else if (e instanceof Mob m && m.hasAI()) {
            m.setAI(false);
            frozenMobs.put(id, m);
        }
        e.setFreezeTicks(e.getMaxFreezeTicks());
        Location l = e.getLocation().add(0, e.getHeight() / 2, 0);
        e.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 30, 0.4, 0.6, 0.4, 0.02);
        e.getWorld().playSound(l, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> unfreeze(e), ticks);
    }

    private void unfreeze(LivingEntity e) {
        UUID id = e.getUniqueId();
        until.remove(id);
        Mob m = frozenMobs.remove(id);
        if (m != null && m.isValid()) m.setAI(true);
        if (e.isValid()) e.setFreezeTicks(0);
    }

    public void shutdown() {
        for (Mob m : frozenMobs.values()) {
            if (m.isValid()) m.setAI(true);
        }
        frozenMobs.clear();
        until.clear();
    }

    // ------------------------------------------------------ blokady gracza

    private boolean fz(Entity e) {
        return e instanceof Player && isFrozen(e);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!fz(e.getPlayer())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            e.setTo(to); // obracać głową można, ruszać się nie
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (fz(e.getDamager())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && fz(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (fz(e.getWhoClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInvOpen(InventoryOpenEvent e) {
        if (fz(e.getPlayer())) e.setCancelled(true);
    }
}
