package fr.eminiumgames.dungeonsinstances.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public class DungeonManager {

    private final Map<String, World> dungeonCache = new HashMap<>();
    private final File dungeonTemplatesFolder = new File("dungeons-templates");

    public void loadDungeonTemplate(String templateName) {
        File templateFolder = new File(dungeonTemplatesFolder, templateName);
        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            Bukkit.getLogger().warning("Template " + templateName + " does not exist or is not a directory.");
            return;
        }

        // Load the template world into the cache
        World world = Bukkit.createWorld(new WorldCreator(templateName));
        if (world != null) {
            dungeonCache.put(templateName, world);
            Bukkit.getLogger().info("Loaded dungeon template: " + templateName);
        } else {
            Bukkit.getLogger().warning("Failed to load dungeon template: " + templateName);
        }
    }

    public World createDungeonInstance(String templateName, String instanceName) {
        if (!dungeonCache.containsKey(templateName)) {
            Bukkit.getLogger().warning("Template " + templateName + " is not loaded.");
            return null;
        }

        File instanceFolder = new File(Bukkit.getWorldContainer(), instanceName);
        if (instanceFolder.exists()) {
            Bukkit.getLogger().warning("Dungeon instance " + instanceName + " already exists.");
            return null;
        }

        File templateFolder = new File(Bukkit.getWorldContainer(), templateName);
        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            Bukkit.getLogger().warning("Template folder for " + templateName + " does not exist.");
            return null;
        }

        try {
            // Copy the template folder to create a new instance
            copyFolder(templateFolder.toPath(), instanceFolder.toPath());
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to create dungeon instance: " + e.getMessage());
            return null;
        }

        // Load the new instance as a world
        World instance = Bukkit.createWorld(new WorldCreator(instanceName));
        if (instance != null) {
            Bukkit.getLogger().info("Created dungeon instance: " + instanceName);
        } else {
            Bukkit.getLogger().warning("Failed to load dungeon instance: " + instanceName);
        }
        return instance;
    }

    public void unloadDungeonInstance(String instanceName) {
        World world = Bukkit.getWorld(instanceName);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            Bukkit.getLogger().info("Unloaded dungeon instance: " + instanceName);
        } else {
            Bukkit.getLogger().warning("Dungeon instance " + instanceName + " is not loaded.");
        }
    }

    private void copyFolder(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path targetPath = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy folder: " + e.getMessage(), e);
            }
        });
    }
}