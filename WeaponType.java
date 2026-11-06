package pl.buffweapons;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public enum WeaponType {
    TRIDENT("trident", "<aqua>Trójząb", "TRIDENT", 10, 7),
    SPEAR("spear", "<gold>Włócznia", "IRON_SPEAR", 10, 15),
    CROSSBOW("crossbow", "<green>Kusza", "CROSSBOW", 15, 20),
    SWORD("sword", "<white>Miecz", "DIAMOND_SWORD", 10, 5),
    AXE("axe", "<red>Topór", "DIAMOND_AXE", 7.5, 5);

    public final String id;
    public final String title;
    private final String iconName;
    private final double defDuration;
    private final double defRequired;

    WeaponType(String id, String title, String iconName, double defDuration, double defRequired) {
        this.id = id;
        this.title = title;
        this.iconName = iconName;
        this.defDuration = defDuration;
        this.defRequired = defRequired;
    }

    public String plainName() {
        return Text.plain(title);
    }

    public Material icon() {
        Material m = Material.matchMaterial(iconName);
        return m != null ? m : Material.IRON_SWORD;
    }

    public double duration(FileConfiguration c) {
        return c.getDouble(id + ".ultimate.duration-seconds", defDuration);
    }

    /** Liczba trafień / seria / sekundy ładowania, zależnie od broni. */
    public double required(FileConfiguration c) {
        String key = switch (this) {
            case AXE -> ".ultimate.charge-seconds";
            case SWORD -> ".ultimate.required-streak";
            default -> ".ultimate.required-hits";
        };
        return c.getDouble(id + key, defRequired);
    }

    public static WeaponType fromId(String s) {
        if (s == null) return null;
        for (WeaponType t : values()) {
            if (t.id.equalsIgnoreCase(s)) return t;
        }
        return null;
    }

    public static WeaponType of(Material m) {
        if (m == null) return null;
        String n = m.name();
        if (n.equals("TRIDENT")) return TRIDENT;
        if (n.equals("CROSSBOW")) return CROSSBOW;
        if (n.endsWith("_SPEAR")) return SPEAR;
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_AXE")) return AXE;
        return null;
    }

    public List<String> buffLines(FileConfiguration c) {
        return switch (this) {
            case TRIDENT -> List.of(
                    "Zasięg " + Text.num(3 + c.getDouble("trident.reach-bonus", 0.5)) + " bloku zamiast 3",
                    "Zamienia się w miecz (tempo ataku miecza i atak obszarowy)",
                    "Wyłącza tarczę przeciwnika, tak jak siekiera");
            case SPEAR -> List.of(
                    "Cooldown prawie taki sam jak miecza (lekko dłuższy)",
                    "Większy podstawowy damage (x" + Text.num(c.getDouble("spear.damage-multiplier", 1.2)) + ")");
            case CROSSBOW -> List.of(
                    "Przebija tarczę",
                    "Szansa na podpalenie przeciwnika (" + Text.num(c.getDouble("crossbow.ignite-chance", 0.25) * 100) + "%)");
            case SWORD -> List.of("Brak buffa");
            case AXE -> List.of(
                    "Co " + c.getInt("axe.freeze-every-crits", 5) + " kryty zamrażasz przeciwnika na "
                            + Text.num(c.getDouble("axe.freeze-seconds", 1)) + " s (nie może nic zrobić)");
        };
    }

    public List<String> ultimateLines(FileConfiguration c) {
        return switch (this) {
            case TRIDENT -> List.of(
                    "Każde trafienie w przeciwnika przywołuje piorun",
                    "Większy damage (x" + Text.num(c.getDouble("trident.ultimate.damage-multiplier", 1.3)) + ")");
            case SPEAR -> List.of(
                    "Doskok do przodu jak Lunge III",
                    "Działa też przy użyciu włóczni prawym przyciskiem myszy");
            case CROSSBOW -> List.of("Power V i Quick Charge V na kuszy");
            case SWORD -> List.of(
                    "Automatyczne crity (bez spadania)",
                    "Cooldown ataku o 50% krótszy",
                    "Niszczy tarczę");
            case AXE -> List.of(
                    "Ataki zadają obrażenia obszarowe",
                    "Cooldown ataku o 50% krótszy");
        };
    }

    public String requirementLine(FileConfiguration c) {
        String r = Text.num(required(c));
        return switch (this) {
            case TRIDENT -> "Uderz " + r + " razy w deszczu lub burzy";
            case SPEAR -> "Trafij " + r + " razy (nie musi być z rzędu)";
            case CROSSBOW -> "Trafij moba lub gracza " + r + " razy (nie musi być z rzędu)";
            case SWORD -> "Trafij " + r + " razy z rzędu, nie dając się uderzyć";
            case AXE -> "Przez " + r + " s kucaj, trzymaj PPM i nie ruszaj się. Nie możesz dostać ani zadać ciosu";
        };
    }
}
