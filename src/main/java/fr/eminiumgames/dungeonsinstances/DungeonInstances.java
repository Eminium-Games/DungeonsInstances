package fr.eminiumgames.dungeonsinstances;

import org.bukkit.plugin.java.JavaPlugin;

import fr.eminiumgames.dungeonsinstances.commands.DungeonCommand;
import fr.eminiumgames.dungeonsinstances.commands.DungeonTabCompleter;
import fr.eminiumgames.dungeonsinstances.commands.PartyCommand;
import fr.eminiumgames.dungeonsinstances.managers.DungeonManager;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonInstances extends JavaPlugin {

    private static DungeonInstances instance;
    private DungeonManager dungeonManager;
    private PartyManager partyManager;

    @Override
    public void onEnable() {
        instance = this;
        dungeonManager = new DungeonManager();
        partyManager = new PartyManager();

        getLogger().info("DungeonInstances plugin enabled.");

        // Register commands and events here
        getCommand("dungeon").setExecutor(new DungeonCommand());
        getCommand("dungeon").setTabCompleter(new DungeonTabCompleter());
        getCommand("party").setExecutor(new PartyCommand());
    }

    @Override
    public void onDisable() {
        getLogger().info("DungeonInstances plugin disabled.");
    }

    public static DungeonInstances getInstance() {
        return instance;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }
}