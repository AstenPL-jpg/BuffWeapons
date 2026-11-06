package pl.buffweapons;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /bronie (przegląd wszystkich broni) i /ultimatesettings (sposoby aktywacji). */
public final class Menus implements Listener {
    private enum Kind {WEAPONS, SETTINGS}

    private static final class Holder implements InventoryHolder {
        final Kind kind;
        Inventory inventory;

        Holder(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] WEAPON_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] METHOD_SLOTS = {11, 13, 15};

    private final BuffWeapons plugin;

    public Menus(BuffWeapons plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- bronie

    public void openWeapons(Player p) {
        Holder h = new Holder(Kind.WEAPONS);
        Inventory inv = Bukkit.createInventory(h, 27, Text.c("<gold><bold>Bronie i ultimate"));
        h.inventory = inv;
        fill(inv);

        FileConfiguration c = plugin.getConfig();
        WeaponType mine = plugin.data().weapon(p);
        WeaponType[] all = WeaponType.values();
        for (int i = 0; i < all.length; i++) {
            inv.setItem(WEAPON_SLOTS[i], weaponItem(all[i], all[i] == mine, c));
        }

        ItemStack info = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = info.getItemMeta();
        m.displayName(Text.c("<light_purple>Reroll broni"));
        m.lore(Text.lore(List.of(
                "<gray>Broń losuje się raz, na początku.",
                "<gray>Potem zmienisz ją tylko Kryształem Rerolla.",
                "",
                "<white>Receptura:",
                "<gray>" + plugin.reroll().recipeDescription())));
        info.setItemMeta(m);
        inv.setItem(22, info);

        p.openInventory(inv);
    }

    private ItemStack weaponItem(WeaponType t, boolean mine, FileConfiguration c) {
        ItemStack it = new ItemStack(t.icon());
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(t.title));
        List<String> lore = new ArrayList<>();
        if (mine) {
            lore.add("<green>✔ To jest Twoja broń");
            lore.add("");
        }
        lore.add("<yellow>Buffy:");
        for (String l : t.buffLines(c)) lore.add("<white> • " + l);
        lore.add("");
        lore.add("<gold>Ultimate:");
        for (String l : t.ultimateLines(c)) lore.add("<white> • " + l);
        lore.add("");
        lore.add("<gold>Czas ultimate: <white>" + Text.num(t.duration(c)) + " s");
        lore.add("<gold>Wymaganie: <white>" + t.requirementLine(c));
        m.lore(Text.lore(lore));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (mine) m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    // -------------------------------------------------------------- ustawienia

    public void openSettings(Player p) {
        Holder h = new Holder(Kind.SETTINGS);
        Inventory inv = Bukkit.createInventory(h, 27, Text.c("<gold><bold>Ustawienia ultimate"));
        h.inventory = inv;
        render(p, inv);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv) {
        fill(inv);
        ActivationMethod[] ms = ActivationMethod.values();
        for (int i = 0; i < ms.length; i++) {
            boolean on = plugin.data().methodEnabled(p, ms[i]);
            ItemStack it = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.c((on ? "<green>✔ " : "<red>✘ ") + "<white>" + ms[i].title));
            m.lore(Text.lore(List.of(
                    "<gray>" + ms[i].description,
                    "",
                    on ? "<green>Włączone" : "<red>Wyłączone",
                    "<yellow>Kliknij, aby przełączyć")));
            it.setItemMeta(m);
            inv.setItem(METHOD_SLOTS[i], it);
        }
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta m = info.getItemMeta();
        m.displayName(Text.c("<white>Jak to działa"));
        m.lore(Text.lore(List.of(
                "<gray>Możesz mieć włączonych kilka sposobów naraz.",
                "<gray>Przynajmniej jeden musi zostać włączony.")));
        info.setItemMeta(m);
        inv.setItem(22, info);
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.empty());
        pane.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    // ----------------------------------------------------------------- kliknięcia

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (h.kind != Kind.SETTINGS || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;

        ActivationMethod[] ms = ActivationMethod.values();
        for (int i = 0; i < ms.length; i++) {
            if (e.getSlot() != METHOD_SLOTS[i]) continue;
            if (!plugin.data().toggleMethod(p, ms[i])) {
                p.sendMessage(Text.msg("<red>Musi zostać włączony przynajmniej jeden sposób aktywacji."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            render(p, top);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
