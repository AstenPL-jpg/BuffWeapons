package pl.buffweapons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

import java.util.concurrent.ThreadLocalRandom;

/** Buffy i efekty ultimate podczas walki. */
public final class CombatListener implements Listener {
    private final BuffWeapons plugin;
    private final NamespacedKey arrowKey;
    /** true, gdy sami zadajemy obrażenia (sweep, AoE, piorun), żeby nie wywołać pętli. */
    private boolean internal = false;

    public CombatListener(BuffWeapons plugin) {
        this.plugin = plugin;
        this.arrowKey = new NamespacedKey(plugin, "buffed_crossbow_arrow");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------- przerywanie serii

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (internal) return;
        if (e.getEntity() instanceof Player p) {
            plugin.ultimates().breakAxe(p, "dostałeś cios");
        }
    }

    // ------------------------------------------------------------ główny hit

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (internal) return;

        if (e.getEntity() instanceof Player victim) {
            plugin.ultimates().breakStreak(victim);
        }

        if (e.getDamager() instanceof Player p) {
            plugin.ultimates().breakAxe(p, "zadałeś cios");
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) melee(e, p);
        } else if (e.getDamager() instanceof AbstractArrow arrow && isBuffedArrow(arrow)
                && arrow.getShooter() instanceof Player shooter) {
            crossbowHit(e, shooter);
        }
    }

    private boolean validTarget(Entity en, Player attacker) {
        return en instanceof LivingEntity && !(en instanceof ArmorStand) && !en.equals(attacker);
    }

    private void melee(EntityDamageByEntityEvent e, Player p) {
        WeaponType assigned = plugin.data().weapon(p);
        if (assigned == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (WeaponType.of(hand.getType()) != assigned) return;
        if (!validTarget(e.getEntity(), p)) return;
        LivingEntity target = (LivingEntity) e.getEntity();

        switch (assigned) {
            case SPEAR -> {
                e.setDamage(e.getDamage() * cfg().getDouble("spear.damage-multiplier", 1.2));
                plugin.ultimates().addProgress(p, WeaponType.SPEAR, 1);
            }
            case TRIDENT -> trident(e, p, target);
            case SWORD -> sword(e, p, target);
            case AXE -> axe(e, p, target);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ trójząb

    private void trident(EntityDamageByEntityEvent e, Player p, LivingEntity target) {
        Ultimates ult = plugin.ultimates();
        boolean crit = isVanillaCrit(p);

        // buff: wyłącza tarczę jak siekiera
        if (target instanceof Player v && v.isBlocking()) {
            disableShield(v, cfg().getInt("trident.shield-disable-ticks", 100));
        }

        // buff: "zamienia się w miecz" = atak obszarowy jak miecz
        if (!crit && !p.isSprinting() && p.isOnGround() && p.getAttackCooldown() > 0.9f) {
            sweep(p, target, cfg().getDouble("trident.sweep-damage", 1.0));
        }

        if (ult.isActive(p, WeaponType.TRIDENT)) {
            e.setDamage(e.getDamage() * cfg().getDouble("trident.ultimate.damage-multiplier", 1.3));
            lightning(p, target);
        } else if (stormExposed(p)) {
            ult.addProgress(p, WeaponType.TRIDENT, 1);
        }
    }

    private boolean stormExposed(Player p) {
        World w = p.getWorld();
        if (!w.hasStorm()) return false;
        // pod dachem deszcz Cię nie moczy
        return w.getHighestBlockYAt(p.getLocation()) <= p.getLocation().getBlockY();
    }

    private void lightning(Player attacker, LivingEntity target) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        double dmg = cfg().getDouble("trident.ultimate.lightning-damage", 4.0);
        int fire = cfg().getInt("trident.ultimate.lightning-fire-ticks", 60);
        // opóźnienie, bo zwykły cios daje klatki nietykalności i pochłonąłby piorun
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            internal = true;
            try {
                target.setNoDamageTicks(0);
                target.damage(dmg, attacker);
            } finally {
                internal = false;
            }
            target.setFireTicks(Math.max(target.getFireTicks(), fire));
        }, 2L);
    }

    private void sweep(Player p, LivingEntity target, double dmg) {
        BoundingBox box = target.getBoundingBox().expand(1.0, 0.25, 1.0);
        internal = true;
        try {
            for (Entity en : target.getWorld().getNearbyEntities(box)) {
                if (!validTarget(en, p) || en.equals(target)) continue;
                if (isOwnPet(en, p)) continue;
                if (p.getLocation().distanceSquared(en.getLocation()) > 9.0) continue;
                ((LivingEntity) en).damage(dmg, p);
            }
        } finally {
            internal = false;
        }
        var l = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.5));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
    }

    // -------------------------------------------------------------- miecz

    private void sword(EntityDamageByEntityEvent e, Player p, LivingEntity target) {
        Ultimates ult = plugin.ultimates();
        if (ult.isActive(p, WeaponType.SWORD)) {
            // automatyczny crit, o ile cios jest naładowany i nie jest już critem
            if (!isVanillaCrit(p) && p.getAttackCooldown() > 0.9f) {
                e.setDamage(e.getDamage() * cfg().getDouble("sword.ultimate.crit-multiplier", 1.5));
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2, 0),
                        15, 0.3, 0.4, 0.3, 0.2);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            }
            if (target instanceof Player v && v.isBlocking()) {
                if (cfg().getBoolean("sword.ultimate.destroy-shield-item", false)) {
                    destroyShield(v);
                } else {
                    disableShield(v, cfg().getInt("sword.ultimate.shield-disable-ticks", 200));
                }
            }
        } else {
            ult.addProgress(p, WeaponType.SWORD, 1);
        }
    }

    // -------------------------------------------------------------- topór

    private void axe(EntityDamageByEntityEvent e, Player p, LivingEntity target) {
        PlayerData d = plugin.data();
        if (isVanillaCrit(p)) {
            int every = Math.max(1, cfg().getInt("axe.freeze-every-crits", 5));
            int c = d.axeCrits(p) + 1;
            if (c >= every) {
                d.setAxeCrits(p, 0);
                int ticks = (int) Math.round(cfg().getDouble("axe.freeze-seconds", 1.0) * 20);
                plugin.freeze().freeze(target, ticks);
                p.sendActionBar(Text.c("<aqua>Zamrożenie!"));
            } else {
                d.setAxeCrits(p, c);
                p.sendActionBar(Text.c("<aqua>Kryty do zamrożenia: <white>" + c + "/" + every));
            }
        }

        if (plugin.ultimates().isActive(p, WeaponType.AXE)) {
            double r = cfg().getDouble("axe.ultimate.aoe-radius", 3.0);
            double frac = cfg().getDouble("axe.ultimate.aoe-damage-fraction", 0.6);
            double dmg = e.getDamage() * frac;
            internal = true;
            try {
                for (Entity en : target.getNearbyEntities(r, r, r)) {
                    if (!validTarget(en, p) || isOwnPet(en, p)) continue;
                    ((LivingEntity) en).damage(dmg, p);
                }
            } finally {
                internal = false;
            }
            var l = target.getLocation().add(0, 0.5, 0);
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 10, r / 2, 0.2, r / 2, 0);
            target.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        }
    }

    // -------------------------------------------------------------- kusza

    private boolean isCrossbow(ItemStack it) {
        return it != null && it.getType() == Material.CROSSBOW;
    }

    private boolean isBuffedArrow(AbstractArrow a) {
        return a.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow a) || a instanceof Trident) return;
        if (!(a.getShooter() instanceof Player p)) return;
        if (plugin.data().weapon(p) != WeaponType.CROSSBOW) return;
        PlayerInventory inv = p.getInventory();
        if (!isCrossbow(inv.getItemInMainHand()) && !isCrossbow(inv.getItemInOffHand())) return;

        a.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.ultimates().isActive(p, WeaponType.CROSSBOW)) {
            // Power V: +3 do bazowych 2 obrażeń strzały
            a.setDamage(a.getDamage() + cfg().getDouble("crossbow.ultimate.power-bonus-damage", 3.0));
        }
    }

    /** Buff: strzała przebija tarczę. Zdejmujemy blokowanie tuż przed zadaniem obrażeń. */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow a) || !isBuffedArrow(a)) return;
        if (e.getHitEntity() instanceof Player v && v.isBlocking()) {
            v.clearActiveItem();
        }
    }

    private void crossbowHit(EntityDamageByEntityEvent e, Player shooter) {
        if (!validTarget(e.getEntity(), shooter)) return;
        LivingEntity target = (LivingEntity) e.getEntity();

        if (ThreadLocalRandom.current().nextDouble() < cfg().getDouble("crossbow.ignite-chance", 0.25)) {
            int ticks = (int) Math.round(cfg().getDouble("crossbow.ignite-seconds", 4) * 20);
            target.setFireTicks(Math.max(target.getFireTicks(), ticks));
        }
        plugin.ultimates().addProgress(shooter, WeaponType.CROSSBOW, 1);
    }

    // ----------------------------------------------------------- pomocnicze

    /** Czy to byłby normalny, "spadający" crit (bez naszych modyfikacji). */
    private boolean isVanillaCrit(Player p) {
        return p.getFallDistance() > 0.0f
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isInWater()
                && !p.hasPotionEffect(PotionEffectType.BLINDNESS)
                && !p.isInsideVehicle()
                && !p.isSprinting()
                && p.getAttackCooldown() > 0.9f;
    }

    private boolean isOwnPet(Entity en, Player p) {
        return en instanceof Tameable t && p.getUniqueId().equals(t.getOwnerUniqueId());
    }

    private void disableShield(Player victim, int ticks) {
        victim.clearActiveItem();
        victim.setCooldown(Material.SHIELD, ticks);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
    }

    private void destroyShield(Player victim) {
        PlayerInventory inv = victim.getInventory();
        victim.clearActiveItem();
        if (inv.getItemInOffHand().getType() == Material.SHIELD) {
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        } else if (inv.getItemInMainHand().getType() == Material.SHIELD) {
            inv.setItemInMainHand(new ItemStack(Material.AIR));
        }
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.8f);
    }
}
