package pl.buffweapons;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * Zasięg i tempo ataku zmieniamy modyfikatorami atrybutów gracza (nie przedmiotu),
 * więc działa niezależnie od materiału i enchantów broni. Modyfikatory są "transient",
 * czyli nie zapisują się do pliku gracza.
 */
public final class Attributes {
    private final BuffWeapons plugin;
    private final NamespacedKey reachKey;
    private final NamespacedKey speedSetKey;
    private final NamespacedKey speedMultKey;

    public Attributes(BuffWeapons plugin) {
        this.plugin = plugin;
        this.reachKey = new NamespacedKey(plugin, "bw_reach");
        this.speedSetKey = new NamespacedKey(plugin, "bw_speed_set");
        this.speedMultKey = new NamespacedKey(plugin, "bw_speed_mult");
    }

    public void update(Player p) {
        WeaponType assigned = plugin.data().weapon(p);
        WeaponType held = WeaponType.of(p.getInventory().getItemInMainHand().getType());
        boolean on = assigned != null && assigned == held;
        var cfg = plugin.getConfig();

        // zasięg
        double reach = (on && assigned == WeaponType.TRIDENT) ? cfg.getDouble("trident.reach-bonus", 0.5) : 0;
        AttributeInstance reachInst = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (reachInst != null) {
            setModifier(reachInst, reachKey, reach, AttributeModifier.Operation.ADD_NUMBER);
        }

        // tempo ataku
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;

        double target = 0;
        if (on && assigned == WeaponType.SPEAR) target = cfg.getDouble("spear.attack-speed", 1.5);
        if (on && assigned == WeaponType.TRIDENT) target = cfg.getDouble("trident.sword-attack-speed", 1.6);

        double mult = 0;
        if (on && plugin.ultimates().isActive(p, assigned)) {
            if (assigned == WeaponType.SWORD) mult = cfg.getDouble("sword.ultimate.attack-speed-multiplier", 2.0) - 1;
            if (assigned == WeaponType.AXE) mult = cfg.getDouble("axe.ultimate.attack-speed-multiplier", 2.0) - 1;
        }

        if (target > 0) {
            setModifier(speed, speedMultKey, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            AttributeModifier existing = find(speed, speedSetKey);
            double raw = speed.getValue() - (existing == null ? 0 : existing.getAmount());
            setModifier(speed, speedSetKey, target - raw, AttributeModifier.Operation.ADD_NUMBER);
        } else {
            setModifier(speed, speedSetKey, 0, AttributeModifier.Operation.ADD_NUMBER);
            setModifier(speed, speedMultKey, mult, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
    }

    public void clear(Player p) {
        AttributeInstance reachInst = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (reachInst != null) setModifier(reachInst, reachKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) {
            setModifier(speed, speedSetKey, 0, AttributeModifier.Operation.ADD_NUMBER);
            setModifier(speed, speedMultKey, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
    }

    private static AttributeModifier find(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) return m;
        }
        return null;
    }

    private static void setModifier(AttributeInstance inst, NamespacedKey key, double amount,
                                    AttributeModifier.Operation op) {
        AttributeModifier existing = find(inst, key);
        if (Math.abs(amount) < 1e-6) {
            if (existing != null) inst.removeModifier(existing);
            return;
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1e-6) return;
            inst.removeModifier(existing);
        }
        inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }
}
