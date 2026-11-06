package pl.buffweapons;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {
    private final BuffWeapons plugin;

    public Commands(BuffWeapons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase(Locale.ROOT);

        if (name.equals("bronieadmin")) {
            return admin(s, a);
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        switch (name) {
            case "bronie" -> plugin.menus().openWeapons(p);
            case "ultimatesettings" -> plugin.menus().openSettings(p);
            case "uzyjultimate" -> plugin.ultimates().tryActivate(p, ActivationMethod.COMMAND);
            default -> {
            }
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("buffweapons.admin")) {
            s.sendMessage(Text.msg("<red>Brak uprawnień."));
            return true;
        }
        if (a.length < 2) {
            s.sendMessage(Text.msg("Użycie: /bronieadmin <set <gracz> <broń> | reroll <gracz> | ready <gracz> | give <gracz> [ilość] | reload>"));
            return true;
        }
        if (a[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.reroll().registerRecipe();
            s.sendMessage(Text.msg("Przeładowano config."));
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            s.sendMessage(Text.msg("<red>Nie ma takiego gracza online."));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                WeaponType w = a.length > 2 ? WeaponType.fromId(a[2]) : null;
                if (w == null) {
                    s.sendMessage(Text.msg("<red>Podaj broń: trident, spear, crossbow, sword, axe."));
                    return true;
                }
                plugin.reroll().setWeapon(t, w, "Admin ustawił Ci broń");
                s.sendMessage(Text.msg("Ustawiono " + t.getName() + " broń: " + w.title));
            }
            case "reroll" -> {
                plugin.reroll().reroll(t);
                s.sendMessage(Text.msg("Przelosowano broń gracza " + t.getName() + "."));
            }
            case "ready" -> {
                plugin.ultimates().forceReady(t);
                s.sendMessage(Text.msg("Ultimate gracza " + t.getName() + " jest gotowe."));
            }
            case "give" -> {
                int n = 1;
                if (a.length > 2) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(a[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                t.getInventory().addItem(plugin.reroll().createItem(n));
                s.sendMessage(Text.msg("Dano " + n + "x Kryształ Rerolla graczowi " + t.getName() + "."));
            }
            default -> s.sendMessage(Text.msg("<red>Nieznana podkomenda."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!c.getName().equalsIgnoreCase("bronieadmin") || !s.hasPermission("buffweapons.admin")) return out;
        if (a.length == 1) {
            for (String x : List.of("set", "reroll", "ready", "give", "reload")) {
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
            }
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            for (WeaponType w : WeaponType.values()) {
                if (w.id.startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(w.id);
            }
        }
        return out;
    }
}
