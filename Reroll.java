package pl.buffweapons;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Losowanie broni, Kryształ Rerolla i jego receptura. */
public final class Reroll implements Listener {
    private final BuffWeapons plugin;
    private final NamespacedKey recipeKey;
    private final NamespacedKey itemKey;

    public Reroll(BuffWeapons plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "reroll_crystal");
        this.itemKey = new NamespacedKey(plugin, "reroll_item");
    }

    // ------------------------------------------------------------ przedmiot

    public ItemStack createItem(int amount) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c("<light_purple><bold>Kryształ Rerolla"));
        m.lore(Text.lore(List.of(
                "<gray>Kliknij PPM, aby wylosować nową broń.",
                "<gray>Wylosujesz inną niż obecna.",
                "<dark_gray>Postęp ultimate zostanie wyzerowany.")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isReroll(ItemStack it) {
        if (it == null || it.getType() != Material.ECHO_SHARD) return false;
        ItemMeta m = it.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        var cfg = plugin.getConfig();
        ShapedRecipe r = new ShapedRecipe(recipeKey, createItem(1));
        r.shape("CEC", "ENE", "CEC");
        r.setIngredient('C', mat(cfg.getString("reroll.corner"), Material.DIAMOND));
        r.setIngredient('E', mat(cfg.getString("reroll.edge"), Material.ENDER_PEARL));
        r.setIngredient('N', mat(cfg.getString("reroll.center"), Material.NETHER_STAR));
        Bukkit.addRecipe(r);
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    private static Material mat(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    public String recipeDescription() {
        var cfg = plugin.getConfig();
        return "4x " + mat(cfg.getString("reroll.corner"), Material.DIAMOND).name().toLowerCase().replace('_', ' ')
                + " (rogi), 4x " + mat(cfg.getString("reroll.edge"), Material.ENDER_PEARL).name().toLowerCase().replace('_', ' ')
                + " (boki), 1x " + mat(cfg.getString("reroll.center"), Material.NETHER_STAR).name().toLowerCase().replace('_', ' ')
                + " (środek)";
    }

    public void discover(Player p) {
        p.discoverRecipe(recipeKey);
    }

    // ------------------------------------------------------------- losowanie

    /** Losuje broń, jeśli gracz jeszcze żadnej nie ma. */
    public void ensureWeapon(Player p) {
        if (plugin.data().weapon(p) != null) return;
        WeaponType t = randomWeapon(null);
        plugin.data().setWeapon(p, t);
        plugin.ultimates().resetProgress(p);
        plugin.attributes().update(p);
        announce(p, t, "Twoja broń");
    }

    public WeaponType randomWeapon(WeaponType exclude) {
        List<WeaponType> pool = new ArrayList<>(List.of(WeaponType.values()));
        if (exclude != null) pool.remove(exclude);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public void reroll(Player p) {
        setWeapon(p, randomWeapon(plugin.data().weapon(p)), "Nowa broń");
    }

    public void setWeapon(Player p, WeaponType t, String titleText) {
        plugin.ultimates().end(p, false);
        plugin.ultimates().resetProgress(p);
        plugin.data().setWeapon(p, t);
        plugin.data().setAxeCrits(p, 0);
        plugin.attributes().update(p);
        announce(p, t, titleText);
    }

    private void announce(Player p, WeaponType t, String titleText) {
        p.showTitle(Title.title(Text.c(t.title), Text.c("<gray>" + titleText + ". Szczegóły: <white>/bronie")));
        p.sendMessage(Text.msg(titleText + ": " + t.title + "<gray>. Zobacz buffy i ultimate w <white>/bronie<gray>."));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // -------------------------------------------------------------- zdarzenia

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isReroll(it)) return;
        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        if (plugin.data().weapon(p) == null) {
            ensureWeapon(p);
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) it.setAmount(it.getAmount() - 1);
        reroll(p);
    }

    /** Kryształ nie może być składnikiem żadnej innej receptury. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof ShapedRecipe sr && recipeKey.equals(sr.getKey())) return;
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isReroll(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
