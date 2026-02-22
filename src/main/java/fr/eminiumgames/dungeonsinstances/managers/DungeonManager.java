package fr.eminiumgames.dungeonsinstances.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

// NBT serialization imports will be accessed via reflection at runtime

import fr.eminiumgames.dungeonsinstances.DungeonInstances;

public class DungeonManager {

    private final Map<String, World> dungeonCache = new HashMap<>();
    private final File dungeonTemplatesFolder = new File("templates-dungeons");
    private final Map<String, SpawnPoint> spawnPoints = new HashMap<>();
    private final File spawnDataFile = new File("plugins/DungeonInstances/spawnPoints.json");

    // store mobs placed in edit mode so they can be resurrected in instances
    private final File mobDataFolder = new File("plugins/DungeonInstances/mobSpawns");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public DungeonManager() {
        loadSpawnPoints();
    }

    /**
     * Represents a spawn point for a dungeon template.
     */
    public static class SpawnPoint {
        public double x, y, z;
        public float yaw, pitch;

        public SpawnPoint() {
        }

        public SpawnPoint(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public void setSpawnPoint(String templateName, Location location) {
        spawnPoints.put(templateName, new SpawnPoint(
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        saveSpawnPoints();
    }

    public Location getSpawnLocation(String templateName, World world) {
        SpawnPoint sp = spawnPoints.get(templateName);
        if (sp != null) {
            return sp.toLocation(world);
        }
        return world.getSpawnLocation();
    }

    private void saveSpawnPoints() {
        try {
            if (!spawnDataFile.getParentFile().exists()) {
                spawnDataFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(spawnDataFile)) {
                gson.toJson(spawnPoints, writer);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to save spawn points: " + e.getMessage());
        }
    }

    private void loadSpawnPoints() {
        if (!spawnDataFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(spawnDataFile)) {
            Map<String, SpawnPoint> loaded = gson.fromJson(reader, new TypeToken<Map<String, SpawnPoint>>() {
            }.getType());
            if (loaded != null) {
                spawnPoints.putAll(loaded);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load spawn points: " + e.getMessage());
        }
    }

    public void loadDungeonTemplate(String templateName) {
        File templateFolder = new File(dungeonTemplatesFolder, templateName);
        Bukkit.getLogger().info("Attempting to load template: " + templateName);
        Bukkit.getLogger().info("Looking for template folder at: " + templateFolder.getAbsolutePath());

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
            Bukkit.getLogger().warning("Template " + templateName
                    + " is not loaded. Please ensure the template is loaded before creating an instance.");
            return null;
        }

        File instanceFolder = new File(Bukkit.getWorldContainer(), instanceName);
        if (instanceFolder.exists()) {
            Bukkit.getLogger().warning(
                    "Dungeon instance " + instanceName + " already exists. Please use a unique instance name.");
            return null;
        }

        File templateFolder = new File(Bukkit.getWorldContainer(), templateName);
        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            Bukkit.getLogger().warning("Template folder for " + templateName
                    + " does not exist or is not a directory. Please check the templates-dungeons folder.");
            return null;
        }

        try {
            // Copy the template folder to create a new instance
            copyFolder(templateFolder.toPath(), instanceFolder.toPath());
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to create dungeon instance: " + e.getMessage()
                    + ". Ensure the server has write permissions.");
            return null;
        }

        // Delete the uid.dat file to avoid duplicate world issues
        File uidFile = new File(instanceFolder, "uid.dat");
        if (uidFile.exists() && !uidFile.delete()) {
            Bukkit.getLogger().warning("Failed to delete uid.dat in " + instanceFolder.getAbsolutePath());
        }

        // Load the new instance as a world
        World instance = Bukkit.createWorld(new WorldCreator(instanceName));
        if (instance != null) {
            Bukkit.getLogger().info("Created dungeon instance: " + instanceName);
            // disable natural mob spawning in edit mode worlds
            if (instanceName.startsWith("editmode_")) {
                instance.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            }
            // ensure all mobs in the new instance have AI enabled so they behave normally
            Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> setAIForWorld(instance, true), 1L);
            // resurrect any mobs placed during edit
            if (templateName != null) {
                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    spawnSavedMobs(templateName, instance);
                }, 2L);
            }
        } else {
            Bukkit.getLogger().warning(
                    "Failed to load dungeon instance: " + instanceName + ". Check if the world folder is valid.");
        }
        return instance;
    }

    /**
     * Quickly set the AI flag for all living entities in the given world.
     * @param world world to operate on (may be null)
     * @param ai true to give entities AI, false to freeze them
     */
    public void setAIForWorld(World world, boolean ai) {
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.LivingEntity ent : world.getLivingEntities()) {
            ent.setAI(ai);
        }
    }

    public void unloadDungeonInstance(String instanceName) {
        World world = Bukkit.getWorld(instanceName);
        if (world != null) {
            // move any players out before unloading to avoid leaving them stranded
            for (org.bukkit.entity.Player p : world.getPlayers()) {
                org.bukkit.Location safe = Bukkit.getWorlds().get(0).getSpawnLocation();
                p.teleport(safe);
                p.sendMessage("[DungeonInstances] You have been moved out of an unloaded instance.");
            }

            Bukkit.unloadWorld(world, false);
            Bukkit.getLogger().info("Unloaded dungeon instance: " + instanceName);
        } else {
            Bukkit.getLogger().info("Dungeon instance " + instanceName + " was not loaded.");
        }

        // always attempt to remove the folder regardless of whether the world was loaded
        File instanceFolder = new File(Bukkit.getWorldContainer(), instanceName);
        if (instanceFolder.exists()) {
            deleteFolder(instanceFolder);
            Bukkit.getLogger().info("Deleted dungeon instance folder: " + instanceFolder.getAbsolutePath());
        } else {
            Bukkit.getLogger().info("No folder found for dungeon instance: " + instanceName);
        }
    }

    public void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                deleteFolder(file);
            }
        }
        folder.delete();
    }

    private void copyFolder(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path targetPath = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    // Skip locked files like session.lock or inaccessible files
                    if (path.getFileName().toString().equals("session.lock")) {
                        return;
                    }
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (AccessDeniedException e) {
                Bukkit.getLogger().warning("Access denied to file: " + path + ". Skipping...");
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy folder: " + path + ". Error: " + e.getMessage(), e);
            }
        });
    }

    public void copyWorld(File source, File target) {
        try {
            copyFolder(source.toPath(), target.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy world: " + e.getMessage(), e);
        }
    }

    /**
     * Remove every non-player living entity from the specified world.
     * Used when creating a fresh instance so that only our custom mobs
     * are spawned.
     */
    public void clearMobs(World world) {
        if (world == null) return;
        for (org.bukkit.entity.Entity ent : world.getEntities()) {
            if (ent instanceof org.bukkit.entity.LivingEntity && !(ent instanceof Player)) {
                ent.remove();
            }
        }
    }

    // NBT helpers using reflection to avoid compile-time NMS dependency
    private static final Map<Class<?>, java.lang.reflect.Method> saveMethodCache = new HashMap<>();

    private String serializeEntityNBT(org.bukkit.entity.Entity e) {
        try {
            Object craftEntity = e.getClass().getMethod("getHandle").invoke(e);
            Class<?> nmsEntityClass = craftEntity.getClass();

            // check cache for previously discovered method
            java.lang.reflect.Method saveMethod = saveMethodCache.get(nmsEntityClass);
            Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
            Object nbtInstance = nbtClass.getConstructor().newInstance();

            if (saveMethod == null) {
                // inspect methods and log them for debugging
                for (java.lang.reflect.Method m : nmsEntityClass.getMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(nbtClass)) {
                        String name = m.getName().toLowerCase();
                        if (name.contains("save") || name.contains("a")) {
                            saveMethod = m;
                            break;
                        }
                    }
                }
                saveMethodCache.put(nmsEntityClass, saveMethod);
                Bukkit.getLogger().info("serializeEntityNBT: chosen method for " + nmsEntityClass.getSimpleName() + " = "
                        + (saveMethod != null ? saveMethod.getName() : "<none>"));
            }

            if (saveMethod != null) {
                // invoke method; it may return the compound or a boolean/void
                Object result = saveMethod.invoke(craftEntity, nbtInstance);
                Object tagObj = result;
                if (!nbtClass.isInstance(tagObj)) {
                    // if the method returned something else, use our instance
                    tagObj = nbtInstance;
                }

                // attempt to strip UUID entries
                try {
                    java.lang.reflect.Method removeMethod = tagObj.getClass().getMethod("remove", String.class);
                    removeMethod.invoke(tagObj, "UUID");
                    removeMethod.invoke(tagObj, "OwnerUUID");
                    removeMethod.invoke(tagObj, "UUIDLeast");
                    removeMethod.invoke(tagObj, "UUIDMost");
                } catch (Exception ignore) {
                }

                String serialized = tagObj.toString();
                serialized = serialized.replaceAll("UUID[LM]?ost?:[0-9-]+", "");
                serialized = serialized.replaceAll("UUID:[0-9L;\\.]+", "");
                Bukkit.getLogger().info("serializeEntityNBT: raw nbt for " + e.getType() + " = " + serialized);
                if ("{}".equals(serialized.trim())) {
                    // if nothing was captured, manually build a tiny map
                    Map<String,Object> manual = new HashMap<>();
                    if (e.getCustomName() != null) manual.put("CustomName", e.getCustomName());
                    if (e.isCustomNameVisible()) manual.put("CustomNameVisible", true);
                    if (e instanceof org.bukkit.entity.Damageable) {
                        manual.put("Health", ((org.bukkit.entity.Damageable)e).getHealth());
                    }
                    if (e.isInvulnerable()) manual.put("Invulnerable", true);
                    if (!manual.isEmpty()) {
                        String jsonMap = gson.toJson(manual);
                        Bukkit.getLogger().info("serializeEntityNBT: created manual map for " + e.getType() + " = " + jsonMap);
                        return jsonMap;
                    }
                }
                return serialized;
            } else {
                Bukkit.getLogger().warning("No suitable NBT save method for " + nmsEntityClass.getName() + "; falling back to Bukkit serialization.");
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Reflection NBT failed for " + e + ": " + ex.getMessage());
        }

        // fallback: use Bukkit's ConfigurationSerializable interface
        try {
            if (e instanceof org.bukkit.configuration.serialization.ConfigurationSerializable) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = ((org.bukkit.configuration.serialization.ConfigurationSerializable) e)
                        .serialize();
                String jsonMap = gson.toJson(map);
                Bukkit.getLogger().info("serializeEntityNBT: fallback map for " + e.getType() + " = " + jsonMap);
                return jsonMap;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Fallback serialize failed for " + e + ": " + ex.getMessage());
        }
        return null;
    }

    private void applyEntityNBT(org.bukkit.entity.Entity e, String nbt) {
        // quick heuristics: if the string contains quotes it is likely the JSON map fallback
        if (nbt != null && nbt.contains("\"") && nbt.trim().startsWith("{")) {
            try {
                Map<String, Object> map = gson.fromJson(nbt, new TypeToken<Map<String, Object>>() {}.getType());
                applySerializedMap(e, map);
                return;
            } catch (Exception ex) {
                Bukkit.getLogger().warning("Failed to apply JSON map to " + e + ": " + ex.getMessage());
                // fall through to NBT attempt
            }
        }

        try {
            Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
            Class<?> parserClass = Class.forName("net.minecraft.nbt.MojangsonParser");
            java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
            Object tag = parseMethod.invoke(null, nbt);
            Object craftEntity = e.getClass().getMethod("getHandle").invoke(e);
            Class<?> nmsEntityClass = craftEntity.getClass();
            nmsEntityClass.getMethod("load", nbtClass).invoke(craftEntity, tag);
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Couldn't apply NBT to " + e + ": " + ex.getMessage());
        }
    }

    // apply a Bukkit serialized map back to an entity; handles a few common keys
    private void applySerializedMap(org.bukkit.entity.Entity e, Map<String, Object> map) {
        if (map == null || map.isEmpty()) return;
        // custom name
        if (map.containsKey("CustomName")) {
            e.setCustomName((String) map.get("CustomName"));
        }
        if (map.containsKey("CustomNameVisible")) {
            e.setCustomNameVisible(Boolean.TRUE.equals(map.get("CustomNameVisible")));
        }
        if (e instanceof org.bukkit.entity.Damageable && map.containsKey("Health")) {
            Object h = map.get("Health");
            if (h instanceof Number) {
                ((org.bukkit.entity.Damageable) e).setHealth(((Number) h).doubleValue());
            }
        }
        if (map.containsKey("Invulnerable")) {
            e.setInvulnerable(Boolean.TRUE.equals(map.get("Invulnerable")));
        }
        // more fields can be added as needed
    }

    public boolean isEditMode(String worldName) {
        return worldName.startsWith("editmode_");
    }

    /**
     * Data structure representing a placed mob.
     */
    public static class MobData {
        public String type;
        public double x, y, z;
        public float yaw, pitch;
        public String nbt;        // full NBT string excluding UUID
    }

    private File mobFileFor(String templateName) {
        if (!mobDataFolder.exists()) {
            mobDataFolder.mkdirs();
        }
        return new File(mobDataFolder, templateName + ".json");
    }

    public void saveEditMobs(String templateName, World editWorld) {
        List<MobData> list = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : editWorld.getEntities()) {
            if (e instanceof org.bukkit.entity.LivingEntity && !(e instanceof Player)) {
                MobData d = new MobData();
                d.type = e.getType().name();
                Location loc = e.getLocation();
                d.x = loc.getX(); d.y = loc.getY(); d.z = loc.getZ();
                d.yaw = loc.getYaw(); d.pitch = loc.getPitch();
                d.nbt = serializeEntityNBT(e);
                if (d.nbt == null) {
                    Bukkit.getLogger().warning("NBT serialization returned null for " + e.getType() + " at "
                            + loc.toVector());
                }
                list.add(d);
            }
        }
        try (FileWriter w = new FileWriter(mobFileFor(templateName))) {
            gson.toJson(list, w);
        } catch (IOException ex) {
            Bukkit.getLogger().severe("Failed to save edit mobs: " + ex.getMessage());
        }
    }

    public java.util.List<MobData> loadEditMobs(String templateName) {
        File f = mobFileFor(templateName);
        if (!f.exists()) return java.util.Collections.emptyList();
        try (java.io.FileReader r = new java.io.FileReader(f)) {
            java.util.List<MobData> l = gson.fromJson(r, new TypeToken<java.util.List<MobData>>(){}.getType());
            return l != null ? l : java.util.Collections.emptyList();
        } catch (IOException ex) {
            Bukkit.getLogger().severe("Failed to load edit mobs: " + ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public void spawnSavedMobs(String templateName, World world) {
        java.util.List<MobData> list = loadEditMobs(templateName);
        for (MobData d : list) {
            try {
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(d.type);
                Location loc = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
                org.bukkit.entity.LivingEntity ent = (org.bukkit.entity.LivingEntity) world.spawnEntity(loc, type);
                // apply stored NBT if present
                if (d.nbt != null) {
                    applyEntityNBT(ent, d.nbt);
                }
                ent.setAI(true);
            } catch (IllegalArgumentException ignored) {
                Bukkit.getLogger().warning("Unknown mob type when spawning saved mob: " + d.type);
            }
        }
    }
}