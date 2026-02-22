package fr.eminiumgames.dungeonsinstances;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.eminiumgames.dungeonsinstances.commands.DungeonCommand;
import fr.eminiumgames.dungeonsinstances.commands.DungeonTabCompleter;
import fr.eminiumgames.dungeonsinstances.managers.DungeonManager;
import fr.eminiumgames.dungeonsinstances.managers.DungeonScoreboardManager;
import fr.eminiumgames.dungeonsinstances.managers.PartyManager;

public class DungeonInstances extends JavaPlugin implements Listener {

    private static DungeonInstances instance;
    private DungeonManager dungeonManager;
    private PartyManager partyManager;
    private DungeonScoreboardManager scoreboardManager;

    // remember the world a player died in so respawn logic can use it
    private final java.util.Map<java.util.UUID, String> deathWorlds = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        dungeonManager = new DungeonManager();
        partyManager = new PartyManager();
        scoreboardManager = new DungeonScoreboardManager();
        scoreboardManager.start();

        getLogger().info("DungeonInstances plugin enabled.");

        // Register commands and events here
        getCommand("dungeon").setExecutor(new DungeonCommand());
        getCommand("dungeon").setTabCompleter(new DungeonTabCompleter());

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        // schedule a task to continually enforce NoAI on edit‑mode worlds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().startsWith("editmode_")) {
                    for (LivingEntity ent : w.getLivingEntities()) {
                        if (ent.hasAI()) {
                            ent.setAI(false);
                        }
                    }
                }
            }
        }, 0L, 20L); // every second

        // Load all dungeon templates at startup but do *not* populate mobs or
        // clear natural spawns.  this avoids touching the source worlds while
        // still making them available for instance creation later.
        File templatesFolder = new File(getDataFolder().getParentFile().getParentFile(), "templates-dungeons");
        if (templatesFolder.exists() && templatesFolder.isDirectory()) {
            File[] templateFolders = templatesFolder.listFiles(File::isDirectory);
            if (templateFolders != null) {
                for (File template : templateFolders) {
                    dungeonManager.loadDungeonTemplate(template.getName(), false);
                }
            }
        } else {
            getLogger().warning(
                    "The templates-dungeons folder does not exist or is not a directory. No dungeon templates were loaded.");
        }
        // only inspect worlds that were explicitly created by this plugin.
        // we do *not* iterate every world looking for mobs or chunks; the
        // prefix check prevents accidental work on vanilla or unrelated worlds.
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().startsWith("editmode_")) {
                String tpl = w.getName().substring("editmode_".length());
                dungeonManager.startAutoSave(w, tpl);
            }
        }

        // Purge all instance worlds on plugin reload
        File worldContainer = Bukkit.getWorldContainer();
        File[] instanceFolders = worldContainer
                .listFiles((file) -> file.isDirectory() && file.getName().startsWith("instance_"));

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
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
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

    public DungeonScoreboardManager getScoreboardManager() {
        return scoreboardManager;
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
        Player player = event.getPlayer();
        World previousWorld = event.getFrom();
        World currentWorld = player.getWorld();

        // Remove scoreboard when leaving an instance world
        if (previousWorld.getName().startsWith("instance_") && !currentWorld.getName().startsWith("instance_")) {
            scoreboardManager.removeScoreboard(player);
        }

        // Check if the previous world is an instance world
        if (previousWorld.getName().startsWith("instance_")) {
            // If the world is empty, unload and delete the instance
            if (previousWorld.getPlayers().isEmpty()) {
                dungeonManager.unloadDungeonInstance(previousWorld.getName());
            }
        }
    }

    // @EventHandler
    // public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
    //     World w = event.getLocation().getWorld();
    //     if (w != null && w.getName().startsWith("editmode_")) {
    //         if (event.getEntity() instanceof LivingEntity) {
    //             ((LivingEntity) event.getEntity()).setAI(false);
    //         }
    //         // auto-save mobs immediately when they appear in an editor world
    //         String tpl = w.getName().substring("editmode_".length());
    //         dungeonManager.saveEditMobs(tpl, w);
    //     }
    // }

    /**
     * Remember the world of a player's death. We store this here instead of
     * relying on event.getPlayer().getWorld() in the respawn listener because
     * by the time PlayerRespawnEvent fires the player has already been moved to
     * the respawn world, which is exactly what was tripping us up.
     */
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getEntity() != null) {
            Bukkit.getLogger()
                    .info("[DungeonInstances] PlayerDeathEvent triggered in " + event.getEntity().getWorld().getName());
            deathWorlds.put(event.getEntity().getUniqueId(), event.getEntity().getWorld().getName());
        }
    }

    /**
     * Ensure that players who die inside a dungeon instance respawn at the
     * configured spawn point for that dungeon (or the world spawn if none is
     * defined) rather than at the global lobby.
     */
    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        String worldName = world.getName();

        // Always log where the respawn event is fired, for diagnostic purposes.
        Bukkit.getLogger().info("[DungeonInstances] PlayerRespawnEvent triggered in " + worldName);
        player.sendMessage("[DungeonInstances] Respawn event fired in world " + worldName);

        // if there is a recorded death world use that instead of current world
        String deathWorld = deathWorlds.remove(player.getUniqueId());
        if (deathWorld != null) {
            Bukkit.getLogger().info("[DungeonInstances] player died in " + deathWorld);
            player.sendMessage("[DungeonInstances] You died in " + deathWorld);
            worldName = deathWorld;
            world = Bukkit.getWorld(worldName);
        }

        if (worldName != null && worldName.startsWith("instance_")) {
            String remainder = worldName.substring("instance_".length());
            // template name is everything before the last underscore (uuid)
            int lastIdx = remainder.lastIndexOf('_');
            String templateName = lastIdx == -1 ? remainder : remainder.substring(0, lastIdx);

            Bukkit.getLogger().info("[DungeonInstances] resolving spawn for template '" + templateName + "'");
            Bukkit.getLogger()
                    .info("[DungeonInstances] event default respawn location was " + event.getRespawnLocation());
            Bukkit.getLogger().info("[DungeonInstances] world spawn location is " + world.getSpawnLocation());

            org.bukkit.Location spawnLoc = dungeonManager.getSpawnLocation(templateName, world);
            Bukkit.getLogger().info("[DungeonInstances] computed spawnLoc = " + spawnLoc);

            if (spawnLoc == null) {
                Bukkit.getLogger().warning("[DungeonInstances] no spawn point found for template '"
                        + templateName + "' in world " + worldName + "; using world spawn");
                spawnLoc = world.getSpawnLocation();
            }

            // force respawn location for the event
            event.setRespawnLocation(spawnLoc);
            Bukkit.getLogger().info("[DungeonInstances] setting respawn location to " + spawnLoc);
        }
    }
}