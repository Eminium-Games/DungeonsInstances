package fr.eminiumgames.dungeonsinstances;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.World;
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
    // players who have recently died and are about to respawn; used to avoid
    // unloading the instance because they temporarily leave it during the
    // vanilla respawn teleport.
    private final java.util.Set<java.util.UUID> pendingRespawn = new java.util.HashSet<>();

    @Override
    public void onEnable() {
        // ensure we have a spawnPoints.json inside the plugin folder so that
        // the manager can load saved spawn locations.  if the file is already
        // present we leave it alone; otherwise copy the built‑in default from
        // the JAR resources (src/main/resources/spawnPoints.json).
        File spawnFile = new File(getDataFolder(), "spawnPoints.json");
        if (!spawnFile.exists()) {
            try (java.io.InputStream in = getResource("spawnPoints.json")) {
                if (in != null) {
                    spawnFile.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, spawnFile.toPath());
                    getLogger().info("Installed default spawnPoints.json into plugin folder.");
                }
            } catch (IOException e) {
                getLogger().warning("Failed to copy default spawnPoints.json: " + e.getMessage());
            }
        }

        // basic state needs to exist as early as possible
        instance = this;
        dungeonManager = new DungeonManager();
        partyManager = new PartyManager();
        scoreboardManager = new DungeonScoreboardManager();
        scoreboardManager.start();

        getLogger().info("DungeonInstances plugin enabled.");

        // Register commands and events here
        getCommand("dungeon").setExecutor(new DungeonCommand());
        getCommand("dungeon").setTabCompleter(new DungeonTabCompleter());
        getServer().getPluginManager().registerEvents(this, this);

        // Load all dungeon templates at startup but do *not* populate mobs or
        // clear natural spawns.  this avoids touching the source worlds while
        // still making them available for instance creation later.
        File templatesFolder = new File(getDataFolder().getParentFile().getParentFile(), "templates-dungeons");

        if (!templatesFolder.exists() || !templatesFolder.isDirectory()) {
            // create parent directory if needed
            if (!templatesFolder.exists()) {
                templatesFolder.mkdirs();
            }
            getLogger().info("templates-dungeons folder missing; creating and installing default template(s).");
            try {
                installDefaultTemplates(templatesFolder);
            } catch (IOException e) {
                getLogger().severe("Failed to install default dungeon templates: " + e.getMessage());
            }
            // installing templates may have added a spawnPoints.json (template
            // creator writes it after the plugin has started); reload so we
            // pick up any new data immediately.
            if (dungeonManager != null) {
                dungeonManager.reloadSpawnPoints();
            }
        }

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
        // auto-save has been disabled; no per-world tasks need to be started

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

        // schedule a task to continually enforce NoAI on edit‑mode worlds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().startsWith("editmode_")) {
                    // reuse manager helper so that persistence is also applied
                    dungeonManager.setAIForWorld(w, false);
                }
            }
        }, 0L, 20L); // every second
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

    /**
     * When the templates folder is empty or missing we ship a small built‑in
     * dungeon so that the plugin works out of the box. The world is stored
     * inside the JAR as a zip under /default-templates/manaria.zip. This
     * method unpacks every entry into the provided destination directory.
     */
    private void installDefaultTemplates(File templatesFolder) throws IOException {
        // unzip manaria.zip into templatesFolder/manaria
        try (java.io.InputStream in = getResource("default-templates/manaria.zip");
                java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(in)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(templatesFolder, entry.getName().replaceFirst("^.*?manaria/", "manaria/"));
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zip.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zip.closeEntry();
            }
        }

        // also install default mob spawn data for the template
        File mobDir = new File(getDataFolder(), "mobSpawns");
        mobDir.mkdirs();
        File manariaJsonDest = new File(mobDir, "manaria.json");
        if (!manariaJsonDest.exists()) {
            try (java.io.InputStream in = getResource("default-templates/manaria.json")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, manariaJsonDest.toPath());
                    getLogger().info("Installed default manaria.json into mobSpawns directory.");
                }
            }
        }
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
            // if player was just respawning we may temporarily leave and come
            // back, so skip unload until respawn handler finishes.
            if (pendingRespawn.contains(player.getUniqueId())) {
                return;
            }
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
            // mark as pending so we don't unload the instance when the player
            // is moved to the lobby automatically by vanilla.
            pendingRespawn.add(event.getEntity().getUniqueId());
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
            // do NOT clear pendingRespawn just yet; another plugin may move the
            // player after this event. we will remove the flag after the delayed
            // teleport below.
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
            final org.bukkit.Location chosenLoc = spawnLoc;

            // force respawn location for the event
            event.setRespawnLocation(chosenLoc);
            Bukkit.getLogger().info("[DungeonInstances] setting respawn location to " + chosenLoc);

            // schedule a small repeated task to ensure the player stays in the
            // instance even if another plugin warps them out again. we'll try
            // every tick for a short period and clear pendingRespawn only once the
            // player is observed in the instance world.
            final org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];
            taskHolder[0] = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                int tries = 0;
                int lastMessageSec = -1;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        pendingRespawn.remove(player.getUniqueId());
                        taskHolder[0].cancel();
                        return;
                    }
                    // always teleport every tick to our chosen location

                    World pw = player.getWorld();
                    if (pw != null && pw.getName().startsWith("instance_")) {
                        pendingRespawn.remove(player.getUniqueId());
                        taskHolder[0].cancel();
                        return;
                    }

                    player.teleport(chosenLoc);

                    if (++tries >= 200) { // after 10s stop trying
                        pendingRespawn.remove(player.getUniqueId());
                        taskHolder[0].cancel();
                    }
                }
            }, 20L, 30L);
        }
    }
}