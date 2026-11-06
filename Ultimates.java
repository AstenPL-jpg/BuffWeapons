package pl.buffweapons;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Postęp, gotowość, aktywacja i wygasanie ultimate. */
public final class Ultimates {
    private final BuffWeapons plugin;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Integer> axeCharge = new HashMap<>();
    private final Map<UUID, Integer> lastUseClick = new HashMap<>();
    private final NamespacedKey qcKey;

    private record Active(WeaponType type, int endTick, int totalTicks, BossBar bar) {
    }

    public Ultimates(BuffWeapons plugin) {
        this.plugin = plugin;
        this.qcKey = new NamespacedKey(plugin, "orig_quick_charge");
    }

    private PlayerData data() {
        return plugin.data();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---------------------------------------------------------------- stan

    public boolean isActive(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public boolean isActive(Player p, WeaponType t) {
        Active a = active.get(p.getUniqueId());
        return a != null && a.type == t;
    }

    public boolean isCharging(Player p) {
        return axeCharge.containsKey(p.getUniqueId());
    }

    public void noteUseClick(Player p) {
        lastUseClick.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    // ------------------------------------------------------------- postęp

    /** Dodaje postęp (trafienia / seria). Nic nie robi, gdy ultimate już gotowe lub trwa. */
    public void addProgress(Player p, WeaponType t, int amount) {
        if (data().weapon(p) != t || data().ready(p) || isActive(p)) return;
        int req = (int) Math.round(t.required(cfg()));
        int now = Math.min(req, data().progress(p) + amount);
        data().setProgress(p, now);
        if (now >= req) {
            setReady(p);
        } else {
            p.sendActionBar(Text.c("<gold>Ultimate: " + Text.bar(now / (double) req, 14)
                    + " <white>" + now + "/" + req));
        }
    }

    /** Miecz: seria przerwana, kiedy ktoś Cię uderzy. */
    public void breakStreak(Player victim) {
        if (data().weapon(victim) != WeaponType.SWORD || data().ready(victim) || isActive(victim)) return;
        if (data().progress(victim) > 0) {
            data().setProgress(victim, 0);
            victim.sendActionBar(Text.c("<red>Seria przerwana"));
        }
    }

    /** Topór: ładowanie przerwane. */
    public void breakAxe(Player p, String reason) {
        if (axeCharge.remove(p.getUniqueId()) != null) {
            p.sendActionBar(Text.c("<red>Ładowanie przerwane: " + reason));
        }
    }

    public void resetProgress(Player p) {
        data().setProgress(p, 0);
        data().setReady(p, false);
        axeCharge.remove(p.getUniqueId());
    }

    public void forceReady(Player p) {
        if (data().weapon(p) == null || isActive(p)) return;
        setReady(p);
    }

    private void setReady(Player p) {
        WeaponType t = data().weapon(p);
        if (t == null) return;
        data().setReady(p, true);
        data().setProgress(p, (int) Math.round(t.required(cfg())));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
        p.showTitle(net.kyori.adventure.title.Title.title(
                Text.c("<gold><bold>ULTIMATE GOTOWE"),
                Text.c("<gray>Aktywacja: <white>" + enabledMethods(p))));
        p.sendMessage(Text.msg("Ultimate (" + t.plainName() + ") jest gotowe. Aktywacja: <white>" + enabledMethods(p)));
    }

    public String enabledMethods(Player p) {
        StringBuilder sb = new StringBuilder();
        for (ActivationMethod m : ActivationMethod.values()) {
            if (data().methodEnabled(p, m)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(m == ActivationMethod.COMMAND ? "/uzyjultimate" : m.title);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------- aktywacja

    public void tryActivate(Player p, ActivationMethod via) {
        if (!data().methodEnabled(p, via)) {
            p.sendMessage(Text.msg("<red>Ten sposób aktywacji jest wyłączony. Zmienisz to w /ultimatesettings."));
            return;
        }
        WeaponType t = data().weapon(p);
        if (t == null) return;
        if (isActive(p)) {
            p.sendActionBar(Text.c("<yellow>Ultimate już trwa"));
            return;
        }
        if (!data().ready(p)) {
            if (t == WeaponType.AXE) {
                p.sendActionBar(Text.c("<red>Ultimate niegotowe. <gray>Naładuj je: kucaj, trzymaj PPM i stój w miejscu"));
            } else {
                int req = (int) Math.round(t.required(cfg()));
                p.sendActionBar(Text.c("<red>Ultimate niegotowe: <white>" + data().progress(p) + "/" + req));
            }
            return;
        }
        start(p, t);
    }

    private void start(Player p, WeaponType t) {
        data().setReady(p, false);
        data().setProgress(p, 0);
        axeCharge.remove(p.getUniqueId());

        int total = (int) Math.round(t.duration(cfg()) * 20);
        BossBar.Color color = switch (t) {
            case TRIDENT -> BossBar.Color.BLUE;
            case SPEAR -> BossBar.Color.YELLOW;
            case CROSSBOW -> BossBar.Color.GREEN;
            case SWORD -> BossBar.Color.WHITE;
            case AXE -> BossBar.Color.RED;
        };
        BossBar bar = BossBar.bossBar(Text.c("<gold>Ultimate: " + t.title), 1f, color, BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        active.put(p.getUniqueId(), new Active(t, Bukkit.getCurrentTick() + total, total, bar));

        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        p.sendMessage(Text.msg("Ultimate <gold>" + t.plainName() + "<gray> aktywne na <white>"
                + Text.num(t.duration(cfg())) + " s<gray>!"));

        if (t == WeaponType.CROSSBOW) applyCrossbow(p);
        plugin.attributes().update(p);
    }

    public void end(Player p, boolean expired) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        p.hideBossBar(a.bar);
        if (a.type == WeaponType.CROSSBOW) restoreInventory(p);
        plugin.attributes().update(p);
        if (expired) {
            p.sendMessage(Text.msg("Ultimate się skończyło."));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1f);
        }
    }

    public void onQuit(Player p) {
        end(p, false);
        axeCharge.remove(p.getUniqueId());
        lastUseClick.remove(p.getUniqueId());
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p, false);
        }
        active.clear();
        axeCharge.clear();
    }

    // -------------------------------------------------------------- tick

    public void tick() {
        int now = Bukkit.getCurrentTick();
        for (UUID id : new ArrayList<>(active.keySet())) {
            Active a = active.get(id);
            Player p = Bukkit.getPlayer(id);
            if (a == null) continue;
            if (p == null || !p.isOnline()) {
                active.remove(id);
                continue;
            }
            int left = a.endTick - now;
            if (left <= 0) {
                end(p, true);
                continue;
            }
            a.bar.progress(Math.max(0f, Math.min(1f, left / (float) a.totalTicks)));
            if (a.type == WeaponType.CROSSBOW && now % 10 == 0) applyCrossbow(p);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isSneaking() || axeCharge.containsKey(p.getUniqueId())) tickAxe(p, now);
        }
    }

    private void tickAxe(Player p, int now) {
        UUID id = p.getUniqueId();
        if (data().weapon(p) != WeaponType.AXE || data().ready(p) || isActive(p)) {
            axeCharge.remove(id);
            return;
        }
        boolean holding = WeaponType.of(p.getInventory().getItemInMainHand().getType()) == WeaponType.AXE;
        // klient powtarza "użyj przedmiotu" co ok. 4 ticki, dopóki trzymasz PPM
        boolean rightHeld = now - lastUseClick.getOrDefault(id, -1000) <= 6;
        if (holding && p.isSneaking() && rightHeld) {
            int c = axeCharge.merge(id, 1, Integer::sum);
            int need = (int) Math.round(WeaponType.AXE.required(cfg()) * 20);
            if (c >= need) {
                axeCharge.remove(id);
                setReady(p);
            } else {
                p.sendActionBar(Text.c("<red>Ładowanie ultimate: " + Text.bar(c / (double) need, 14)
                        + " <white>" + Text.num(c / 20.0) + "/" + Text.num(need / 20.0) + " s"));
            }
        } else if (axeCharge.remove(id) != null) {
            p.sendActionBar(Text.c("<red>Ładowanie przerwane"));
        }
    }

    // ------------------------------------------------- kusza: Quick Charge

    private void applyCrossbow(Player p) {
        int level = cfg().getInt("crossbow.ultimate.quick-charge-level", 5);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != Material.CROSSBOW) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(qcKey, PersistentDataType.INTEGER)) continue; // już wzmocniona
            pdc.set(qcKey, PersistentDataType.INTEGER, meta.getEnchantLevel(Enchantment.QUICK_CHARGE));
            meta.addEnchant(Enchantment.QUICK_CHARGE, level, true);
            it.setItemMeta(meta);
            inv.setItem(i, it);
        }
    }

    /** Cofa wzmocnienie na jednej kuszy. Zwraca true, jeśli coś zmieniono. */
    public boolean restoreCrossbow(ItemStack it) {
        if (it == null || it.getType() != Material.CROSSBOW) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer orig = pdc.get(qcKey, PersistentDataType.INTEGER);
        if (orig == null) return false;
        meta.removeEnchant(Enchantment.QUICK_CHARGE);
        if (orig > 0) meta.addEnchant(Enchantment.QUICK_CHARGE, orig, true);
        pdc.remove(qcKey);
        it.setItemMeta(meta);
        return true;
    }

    public void restoreInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (restoreCrossbow(it)) inv.setItem(i, it);
        }
    }
}
