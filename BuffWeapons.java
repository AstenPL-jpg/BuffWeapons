package pl.buffweapons;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BuffWeapons extends JavaPlugin {
    private PlayerData data;
    private Ultimates ultimates;
    private Attributes attributes;
    private FreezeManager freeze;
    private Reroll reroll;
    private Menus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        data = new PlayerData(this);
        ultimates = new Ultimates(this);
        attributes = new Attributes(this);
        freeze = new FreezeManager(this);
        reroll = new Reroll(this);
        menus = new Menus(this);

        reroll.registerRecipe();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new InputListener(this), this);
        pm.registerEvents(freeze, this);
        pm.registerEvents(reroll, this);
        pm.registerEvents(menus, this);

        Commands commands = new Commands(this);
        for (String n : List.of("bronie", "uzyjultimate", "ultimatesettings", "bronieadmin")) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(commands);
                pc.setTabCompleter(commands);
            }
        }

        // gracze online przy /reload
        for (Player p : Bukkit.getOnlinePlayers()) {
            reroll.ensureWeapon(p);
            reroll.discover(p);
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            ultimates.tick();
            if (Bukkit.getCurrentTick() % 2 == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) attributes.update(p);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (ultimates != null) ultimates.shutdown();
        if (freeze != null) freeze.shutdown();
        if (attributes != null) {
            for (Player p : Bukkit.getOnlinePlayers()) attributes.clear(p);
        }
        if (reroll != null) reroll.unregisterRecipe();
    }

    public PlayerData data() {
        return data;
    }

    public Ultimates ultimates() {
        return ultimates;
    }

    public Attributes attributes() {
        return attributes;
    }

    public FreezeManager freeze() {
        return freeze;
    }

    public Reroll reroll() {
        return reroll;
    }

    public Menus menus() {
        return menus;
    }
}
