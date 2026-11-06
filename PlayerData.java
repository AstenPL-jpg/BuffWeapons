package pl.buffweapons;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Dane gracza trzymane w PersistentDataContainer, więc przeżywają restart bez żadnych plików. */
public final class PlayerData {
    public static final int DEFAULT_METHODS = ActivationMethod.SHIFT_F.bit | ActivationMethod.COMMAND.bit;

    private final NamespacedKey weaponK;
    private final NamespacedKey readyK;
    private final NamespacedKey progressK;
    private final NamespacedKey methodsK;
    private final NamespacedKey axeCritsK;

    public PlayerData(JavaPlugin plugin) {
        weaponK = new NamespacedKey(plugin, "weapon");
        readyK = new NamespacedKey(plugin, "ult_ready");
        progressK = new NamespacedKey(plugin, "ult_progress");
        methodsK = new NamespacedKey(plugin, "ult_methods");
        axeCritsK = new NamespacedKey(plugin, "axe_crits");
    }

    private PersistentDataContainer pdc(Player p) {
        return p.getPersistentDataContainer();
    }

    public WeaponType weapon(Player p) {
        String s = pdc(p).get(weaponK, PersistentDataType.STRING);
        return WeaponType.fromId(s);
    }

    public void setWeapon(Player p, WeaponType t) {
        pdc(p).set(weaponK, PersistentDataType.STRING, t.id);
    }

    public boolean ready(Player p) {
        Byte b = pdc(p).get(readyK, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void setReady(Player p, boolean v) {
        pdc(p).set(readyK, PersistentDataType.BYTE, (byte) (v ? 1 : 0));
    }

    public int progress(Player p) {
        Integer i = pdc(p).get(progressK, PersistentDataType.INTEGER);
        return i == null ? 0 : i;
    }

    public void setProgress(Player p, int v) {
        pdc(p).set(progressK, PersistentDataType.INTEGER, v);
    }

    public int axeCrits(Player p) {
        Integer i = pdc(p).get(axeCritsK, PersistentDataType.INTEGER);
        return i == null ? 0 : i;
    }

    public void setAxeCrits(Player p, int v) {
        pdc(p).set(axeCritsK, PersistentDataType.INTEGER, v);
    }

    public int methods(Player p) {
        Integer i = pdc(p).get(methodsK, PersistentDataType.INTEGER);
        return i == null ? DEFAULT_METHODS : i;
    }

    public boolean methodEnabled(Player p, ActivationMethod m) {
        return (methods(p) & m.bit) != 0;
    }

    /** Przełącza sposób aktywacji. Zwraca false, gdyby to wyłączyło ostatni włączony. */
    public boolean toggleMethod(Player p, ActivationMethod m) {
        int now = methods(p) ^ m.bit;
        if (now == 0) return false;
        pdc(p).set(methodsK, PersistentDataType.INTEGER, now);
        return true;
    }
}
