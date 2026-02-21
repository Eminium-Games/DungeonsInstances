package fr.eminiumgames.dungeonsinstances;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.eminiumgames.dungeonsinstances.commands.DungeonCommand;
import fr.eminiumgames.dungeonsinstances.commands.DungeonTabCompleter;
import fr.eminiumgames.dungeonsinstances.commands.PartyCommand;
import fr.eminiumgames.dungeonsinstances.commands.PartyTabCompleter;
import fr.eminiumgames.dungeonsinstances.managers.DungeonManager;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonInstances extends JavaPlugin implements Listener {

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
        getCommand("dparty").setExecutor(new PartyCommand());
        getCommand("dparty").setTabCompleter(new PartyTabCompleter());

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Load all dungeon templates at startup
        File templatesFolder = new File(getDataFolder().getParentFile().getParentFile(), "templates-dungeons");
        if (templatesFolder.exists() && templatesFolder.isDirectory()) {
            File[] templateFolders = templatesFolder.listFiles(File::isDirectory);
            if (templateFolders != null) {
                for (File template : templateFolders) {
                    dungeonManager.loadDungeonTemplate(template.getName());
                }
            }
        } else {
            getLogger().warning("The templates-dungeons folder does not exist or is not a directory. No dungeon templates were loaded.");
        }

        // Purge all instance worlds on plugin reload
        File worldContainer = Bukkit.getWorldContainer();
        File[] instanceFolders = worldContainer.listFiles((file) -> file.isDirectory() && file.getName().startsWith("instance_"));

        if (instanceFolders != null) {
            for (File instanceFolder : instanceFolders) {
                dungeonManager.unloadDungeonInstance(instanceFolder.getName());
                deleteFolder(instanceFolder);
            }
            getLogger().info("All dungeon instances have been purged on plugin reload.");
        }
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

    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
        }
        folder.delete();
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        World previousWorld = event.getFrom();

        // Check if the previous world is an instance world
        if (previousWorld.getName().startsWith("instance_")) {
            // If the world is empty, unload and delete the instance
            if (previousWorld.getPlayers().isEmpty()) {
                dungeonManager.unloadDungeonInstance(previousWorld.getName());
            }
        }
    }
}