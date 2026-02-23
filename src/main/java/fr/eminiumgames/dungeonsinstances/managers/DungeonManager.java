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
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

// NBT serialization imports will be accessed via reflection at runtime

import fr.eminiumgames.dungeonsinstances.DungeonInstances;

public class DungeonManager {

    private final Map<String, World> dungeonCache = new HashMap<>();
    // worlds currently undergoing async chunk forcing (by name)
    private final java.util.Set<String> pendingChunkLoads = java.util.Collections
            .synchronizedSet(new java.util.HashSet<>());
    // (auto-save removed per user request)
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

    /**
     * Load a template world into memory. If <code>populateMobs</code> is true
     * the routine will also clear natural spawns and respawn any saved mobs.
     * When called during plugin startup we pass false to avoid touching productive
     * template worlds; admins can load with mobs manually later if needed.
     */
    public void loadDungeonTemplate(String templateName, boolean populateMobs) {
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
            // optionally clear existing mobs and respawn saved edit-mode creatures
            if (populateMobs) {
                clearMobs(world);
                spawnSavedMobs(templateName, world);
            }
        } else {
            Bukkit.getLogger().warning("Failed to load dungeon template: " + templateName);
        }

        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
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
                // editor worlds no longer pre‑force every chunk. the old
                // loadAndForceAllChunksAsync helper (removed long ago) was the
                // culprit when admins complained that the plugin was "loading all
                // chunks in every world" during startup; we let chunks load on
                // demand now.
                // autosave feature disabled
            }
            // ensure all mobs in the new instance have AI enabled so they behave normally
            Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> setAIForWorld(instance, true), 1L);
            // clear any existing creatures copied along with the world and respawn
            // edit-mode mobs
            if (templateName != null) {
                // give the server a bit more breathing room; mobs will spawn after 5 seconds
                Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                    clearMobs(instance);
                    spawnSavedMobs(templateName, instance);
                }, 100L); // 100 ticks = 5s
            }
        } else {
            Bukkit.getLogger().warning(
                    "Failed to load dungeon instance: " + instanceName + ". Check if the world folder is valid.");
        }
        return instance;
    }

    /**
     * Quickly set the AI flag for all living entities in the given world.
     * 
     * @param world world to operate on (may be null)
     * @param ai    true to give entities AI, false to freeze them
     */
    public void setAIForWorld(World world, boolean ai) {
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.LivingEntity ent : world.getLivingEntities()) {
            ent.setAI(ai);

            ent.setPersistent(true); // ensure they don't despawn
            ((LivingEntity) ent).setRemoveWhenFarAway(false);
        }
    }

    public void unloadDungeonInstance(String instanceName) {
        // if chunks still loading, postpone unload
        if (pendingChunkLoads.contains(instanceName)) {
            Bukkit.getLogger().info("Unload deferred until chunks finished for " + instanceName);
            Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(),
                    () -> unloadDungeonInstance(instanceName), 20L);
            return;
        }
        // cancel any auto-save task for this world
        // autosave disabled; nothing to stop

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

        // always attempt to remove the folder regardless of whether the world was
        // loaded
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
        if (world == null)
            return;
        for (org.bukkit.entity.Entity ent : world.getEntities()) {
            if (ent instanceof org.bukkit.entity.LivingEntity && !(ent instanceof Player)) {
                ent.remove();
            }
        }
    }

    /**
     * Force-load every chunk present in the world's region files. Used by
     * explicit admin save operations to make sure mobs in unloaded chunks are
     * also captured. Does not generate new chunks.
     */
    private void forceLoadAllChunks(World world) {
        if (world == null)
            return;
        File regionFolder = new File(world.getWorldFolder(), "region");
        if (!regionFolder.isDirectory())
            return;
        File[] regions = regionFolder.listFiles((f) -> f.getName().endsWith(".mca"));
        if (regions == null)
            return;
        for (File reg : regions) {
            String name = reg.getName(); // format r.<x>.<z>.mca
            String[] parts = name.split("\\.");
            if (parts.length >= 3) {
                try {
                    int rx = Integer.parseInt(parts[1]);
                    int rz = Integer.parseInt(parts[2]);
                    for (int cx = 0; cx < 32; cx++) {
                        for (int cz = 0; cz < 32; cz++) {
                            world.loadChunk(rx * 32 + cx, rz * 32 + cz, false);
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
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
                Bukkit.getLogger()
                        .info("serializeEntityNBT: chosen method for " + nmsEntityClass.getSimpleName() + " = "
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
                    Map<String, Object> manual = new HashMap<>();
                    if (e.getCustomName() != null)
                        manual.put("CustomName", e.getCustomName());
                    if (e.isCustomNameVisible())
                        manual.put("CustomNameVisible", true);
                    if (e instanceof org.bukkit.entity.Damageable) {
                        manual.put("Health", ((org.bukkit.entity.Damageable) e).getHealth());
                    }
                    if (e.isInvulnerable())
                        manual.put("Invulnerable", true);
                    if (!manual.isEmpty()) {
                        String jsonMap = gson.toJson(manual);
                        Bukkit.getLogger()
                                .info("serializeEntityNBT: created manual map for " + e.getType() + " = " + jsonMap);
                        return jsonMap;
                    }
                }
                return serialized;
            } else {
                Bukkit.getLogger().warning("No suitable NBT save method for " + nmsEntityClass.getName()
                        + "; falling back to Bukkit serialization.");
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Reflection NBT failed for " + e + ": " + ex.getMessage());
        }

        // fallback: use Bukkit's ConfigurationSerializable interface
        try {
            if (e instanceof org.bukkit.configuration.serialization.ConfigurationSerializable) {
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
        if (nbt == null) {
            return;
        }

        // try raw Mojangson NBT first – this covers the vast majority of cases
        // and avoids accidentally interpreting valid NBT as a JSON map. if
        // parsing succeeds we return immediately; otherwise fall through to the
        // map-based fallback.
        try {
            Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
            Class<?> parserClass = Class.forName("net.minecraft.nbt.MojangsonParser");
            java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
            Object tag = parseMethod.invoke(null, nbt);
            Object craftEntity = e.getClass().getMethod("getHandle").invoke(e);
            craftEntity.getClass().getMethod("load", nbtClass).invoke(craftEntity, tag);
            // applied full NBT successfully
            return;
        } catch (Exception ignored) {
            // parsing failed, try fallback below
        }

        // fallback to the limited JSON map format we sometimes write when
        // reflection-based serialization couldn’t produce real NBT.
        try {
            Map<String, Object> map = gson.fromJson(nbt, new TypeToken<Map<String, Object>>() {
            }.getType());
            applySerializedMap(e, map);
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Failed to apply JSON map to " + e + ": " + ex.getMessage());
        }
    }

    // apply a Bukkit serialized map back to an entity; handles a few common keys
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void applySerializedMap(org.bukkit.entity.Entity e, Map<String, Object> map) {
        if (map == null || map.isEmpty())
            return;
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

        e.setPersistent(false);
        ((LivingEntity) e).setRemoveWhenFarAway(false);
        // equipment
        if (map.containsKey("Equipment") && e instanceof org.bukkit.entity.LivingEntity) {
            Object eqObj = map.get("Equipment");
            if (eqObj instanceof Map) {
                org.bukkit.inventory.EntityEquipment equipment = ((org.bukkit.entity.LivingEntity) e).getEquipment();
                if (equipment != null) {
                    Map<?, ?> eqMap = (Map<?, ?>) eqObj;
                    try {
                        if (eqMap.containsKey("helmet")) {
                            equipment.setHelmet(org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("helmet")));
                        }
                        if (eqMap.containsKey("chestplate")) {
                            equipment.setChestplate(
                                    org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("chestplate")));
                        }
                        if (eqMap.containsKey("leggings")) {
                            equipment.setLeggings(
                                    org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("leggings")));
                        }
                        if (eqMap.containsKey("boots")) {
                            equipment.setBoots(org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("boots")));
                        }
                        if (eqMap.containsKey("itemInMainHand")) {
                            equipment.setItemInMainHand(
                                    org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("itemInMainHand")));
                        }
                        if (eqMap.containsKey("itemInOffHand")) {
                            equipment.setItemInOffHand(
                                    org.bukkit.inventory.ItemStack.deserialize((Map) eqMap.get("itemInOffHand")));
                        }
                    } catch (Exception ex) {
                        Bukkit.getLogger().warning("Failed to apply equipment map: " + ex.getMessage());
                    }
                }
            }
        }
        // attributes
        if (map.containsKey("Attributes") && e instanceof org.bukkit.entity.LivingEntity) {
            Object attrObj = map.get("Attributes");
            if (attrObj instanceof Map) {
                Map<?, ?> attrs = (Map<?, ?>) attrObj;
                org.bukkit.entity.LivingEntity le = (org.bukkit.entity.LivingEntity) e;
                for (Map.Entry<?, ?> entry : attrs.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                        try {
                            @SuppressWarnings("deprecation")
                            org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute
                                    .valueOf((String) entry.getKey());
                            org.bukkit.attribute.AttributeInstance ai = le.getAttribute(attr);
                            if (ai != null) {
                                ai.setBaseValue(((Number) entry.getValue()).doubleValue());
                            }
                        } catch (IllegalArgumentException ignore) {
                        }
                    }
                }
            }
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
        public String uuid; // original entity UUID (for deduplication)
        public String type;
        public double x, y, z;
        public float yaw, pitch;
        public String nbt; // full NBT string excluding UUID
        public Map<String, Object> extra; // optional additional data (equipment, attributes, etc.)
    }

    private File mobFileFor(String templateName) {
        if (!mobDataFolder.exists()) {
            mobDataFolder.mkdirs();
        }
        return new File(mobDataFolder, templateName + ".json");
    }

    /**
     * Simplest entry point – does not filter and does not force-load chunks.
     * Used by auto-save tasks. Admins should call the overload that allows
     * forcing all chunks to be loaded so nothing is missed.
     */
    public void saveEditMobs(String templateName, World editWorld) {
        // auto-save case: do not force chunks
        saveEditMobs(templateName, editWorld, false, null, 0.0, Double.NEGATIVE_INFINITY);
    }

    /**
     * Save mobs in an edit-world back to the template file. If a centre and
     * radius are supplied only mobs whose location is within the circle will be
     * recorded. This allows the admin to only persist the creatures they
     * spawned nearby while ignoring random natural spawns happening elsewhere.
     */
    public void saveEditMobs(String templateName, World editWorld, Location centre, double radius) {
        // convenience wrapper when only radius/centre filter is desired; do
        // not force-chunks by default
        saveEditMobs(templateName, editWorld, false, centre, radius, Double.NEGATIVE_INFINITY);
    }

    /**
     * Save mobs in an edit-world back to the template file. If a centre and
     * radius are supplied only mobs whose location is within the circle will be
     * recorded. If minY is finite, creatures below that height are ignored.
     * These filters can be combined (both may apply).
     */
    /**
     * Main saving routine. If <code>forceLoadChunks</code> is true we will
     * pre-load every chunk in the world (reading region files) before taking
     * the snapshot; this makes manual admin saves capture mobs in unloaded
     * areas as well. Calling with forceLoadChunks=false replicates the
     * old behaviour where only loaded entities are saved.
     */
    public void saveEditMobs(String templateName, World editWorld, boolean forceLoadChunks,
            Location centre, double radius, double minY) {
        if (editWorld == null) {
            return;
        }
        if (forceLoadChunks) {
            forceLoadAllChunks(editWorld);
        }
        // disable natural spawning while we snapshot the world
        editWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        // save whatever entities are currently loaded; newly loaded chunks from
        // forceLoadAllChunks will be included above.
        doSaveEditMobs(templateName, editWorld, centre, radius, minY);
    }

    private void doSaveEditMobs(String templateName, World editWorld, Location centre, double radius, double minY) {
        if (editWorld == null)
            return;

        File out = mobFileFor(templateName);
        try (FileWriter fw = new FileWriter(out);
                com.google.gson.stream.JsonWriter jw = new com.google.gson.stream.JsonWriter(fw)) {
            jw.setIndent("  ");
            jw.beginArray();
            for (org.bukkit.entity.Entity e : editWorld.getEntities()) {
                if (!(e instanceof org.bukkit.entity.LivingEntity) || e instanceof Player) {
                    continue;
                }
                if (centre != null) {
                    if (e.getLocation().distanceSquared(centre) > radius * radius) {
                        continue;
                    }
                }
                if (minY != Double.NEGATIVE_INFINITY) {
                    if (e.getLocation().getY() < minY) {
                        continue;
                    }
                }
                org.bukkit.entity.LivingEntity le = (org.bukkit.entity.LivingEntity) e;
                MobData d = new MobData();
                d.uuid = e.getUniqueId().toString();
                d.type = e.getType().name();
                Location loc = e.getLocation();
                d.x = loc.getX();
                d.y = loc.getY();
                d.z = loc.getZ();

                // If y <= -45, ignore the mob
                if (d.y <= -45) {
                    continue;
                }

                d.yaw = loc.getYaw();
                d.pitch = loc.getPitch();
                d.nbt = serializeEntityNBT(e);

                Map<String, Object> extras = gatherExtras(le);
                if (!extras.isEmpty()) {
                    d.extra = extras;
                }

                if (d.nbt == null) {
                    Bukkit.getLogger().warning("NBT serialization returned null for " + e.getType() + " at "
                            + loc.toVector());
                }
                gson.toJson(d, MobData.class, jw);
            }
            jw.endArray();
        } catch (IOException ex) {
            Bukkit.getLogger().severe("Failed to write mob data for template " + templateName + ": " + ex.getMessage());
        }

        // clear current creatures and respawn from the newly saved file
        clearMobs(editWorld);
        spawnSavedMobs(templateName, editWorld);
    }

    /**
     * Helper that collects equipment/attributes metadata from a living entity.
     */
    @SuppressWarnings("deprecation")
    private Map<String, Object> gatherExtras(org.bukkit.entity.LivingEntity le) {
        Map<String, Object> extras = new HashMap<>();
        // equipment
        org.bukkit.inventory.EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            Map<String, Object> equipMap = new HashMap<>();
            if (eq.getHelmet() != null) {
                equipMap.put("helmet", eq.getHelmet().serialize());
            }
            if (eq.getChestplate() != null) {
                equipMap.put("chestplate", eq.getChestplate().serialize());
            }
            if (eq.getLeggings() != null) {
                equipMap.put("leggings", eq.getLeggings().serialize());
            }
            if (eq.getBoots() != null) {
                equipMap.put("boots", eq.getBoots().serialize());
            }
            if (eq.getItemInMainHand() != null) {
                equipMap.put("itemInMainHand", eq.getItemInMainHand().serialize());
            }
            if (eq.getItemInOffHand() != null) {
                equipMap.put("itemInOffHand", eq.getItemInOffHand().serialize());
            }
            if (!equipMap.isEmpty()) {
                extras.put("Equipment", equipMap);
            }
        }
        // attributes
        Map<String, Object> attrMap = new HashMap<>();
        for (org.bukkit.attribute.Attribute attr : org.bukkit.attribute.Attribute.values()) {
            org.bukkit.attribute.AttributeInstance ai = le.getAttribute(attr);
            if (ai != null) {
                attrMap.put(attr.name(), ai.getBaseValue());
            }
        }
        if (!attrMap.isEmpty()) {
            extras.put("Attributes", attrMap);
        }
        return extras;
    }

    public java.util.List<MobData> loadEditMobs(String templateName) {
        File f = mobFileFor(templateName);
        if (!f.exists())
            return java.util.Collections.emptyList();
        try (java.io.FileReader r = new java.io.FileReader(f)) {
            java.util.List<MobData> l = gson.fromJson(r, new TypeToken<java.util.List<MobData>>() {
            }.getType());
            return l != null ? l : java.util.Collections.emptyList();
        } catch (IOException ex) {
            Bukkit.getLogger().severe("Failed to load edit mobs: " + ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Spawn all mobs from a saved list, spreading the work over many ticks so
     * that loading the corresponding chunks and applying NBT does not cause
     * a large hitch or crash.
     */
    // helper used by various routines to ensure we only operate on worlds that
    // are under the plugin's control. previously a poorly‑placed call to
    // spawnSavedMobs during startup could be blamed for "loading every chunk in
    // every world" when in reality the method was never meant to touch the
    // main server worlds. we now sanity‑check the world name before proceeding.
    private boolean isDungeonManagedWorld(World world) {
        if (world == null)
            return false;
        String name = world.getName();
        if (name.startsWith("editmode_") || name.startsWith("instance_"))
            return true;
        // template worlds are also legitimate targets when the cache has been
        // populated (used by loadDungeonTemplate when populateMobs==true).
        if (dungeonCache.containsKey(name))
            return true;
        return false;
    }

    public void spawnSavedMobs(String templateName, World world) {
        // defensive guard – if this method ever gets called on a regular world we
        // bail out now to avoid the impression that we are walking every world and
        // force‑loading chunks. such misuse was the source of the original report.
        if (!isDungeonManagedWorld(world)) {
            Bukkit.getLogger().warning("spawnSavedMobs called for non-dungeon world '" +
                    (world != null ? world.getName() : "null") + "' (template " + templateName
                    + "); skipping to avoid loading chunks.");
            return;
        }

        File f = mobFileFor(templateName);
        if (!f.exists()) {
            Bukkit.getLogger().info("spawnSavedMobs: no saved mobs found for template " + templateName);
            return;
        }
        try {
            FileReader fr = new FileReader(f);
            com.google.gson.stream.JsonReader jr = new com.google.gson.stream.JsonReader(fr);
            jr.beginArray();
            final int[] taskId = new int[1];
            final java.util.concurrent.atomic.AtomicInteger readCount = new java.util.concurrent.atomic.AtomicInteger(
                    0);
            taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(DungeonInstances.getInstance(), new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!jr.hasNext()) {
                            jr.endArray();
                            jr.close();
                            fr.close();
                            Bukkit.getScheduler().cancelTask(taskId[0]);
                            return;
                        }
                        MobData d = gson.fromJson(jr, MobData.class);
                        int idx = readCount.getAndIncrement();
                        Bukkit.getLogger()
                                .info("spawnSavedMobs: entry #" + idx + " type=" + d.type + " uuid=" + d.uuid);
                        // skip if an entity with the same uuid already exists in *this* world
                        // if (d.uuid != null) {
                        // try {
                        // java.util.UUID orig = java.util.UUID.fromString(d.uuid);
                        // // world.getEntity(UUID) does not exist; scan current world entities
                        // org.bukkit.entity.Entity existing = null;
                        // for (org.bukkit.entity.Entity e : world.getEntities()) {
                        // if (orig.equals(e.getUniqueId())) {
                        // existing = e;
                        // break;
                        // }
                        // }
                        // if (existing != null) {
                        // Bukkit.getLogger().info("spawnSavedMobs: skipping mob " + d.type + " with
                        // uuid " + d.uuid + " because it already exists in world " + world.getName());
                        // return;
                        // }
                        // } catch (IllegalArgumentException ignored) {
                        // // malformed uuid; we'll just try to spawn normally
                        // }
                        // }
                        try {
                            org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(d.type);
                            Location loc = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
                            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                                world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                            }
                            org.bukkit.entity.Entity spawned = world.spawnEntity(loc, type);
                            if (spawned == null) {
                                Bukkit.getLogger().warning(
                                        "spawnSavedMobs: spawnEntity returned null for " + d.type + " at " + loc);
                            } else if (spawned instanceof org.bukkit.entity.LivingEntity) {
                                org.bukkit.entity.LivingEntity ent = (org.bukkit.entity.LivingEntity) spawned;
                                // ensure the entity is flagged persistent so vanilla will not
                                // despawn it when no players are nearby
                                try {
                                    ent.setPersistent(true);
                                } catch (NoSuchMethodError | NoClassDefFoundError ignore) {
                                    // older API versions may not have this method; it’s okay
                                }
                                // restore original UUID if possible (for deduplication/persistence)
                                if (d.uuid != null) {
                                    try {
                                        java.util.UUID orig = java.util.UUID.fromString(d.uuid);
                                        Object nms = ent.getClass().getMethod("getHandle").invoke(ent);
                                        nms.getClass().getMethod("setUniqueId", java.util.UUID.class).invoke(nms, orig);
                                    } catch (Exception ignore) {
                                        // if reflection fails or method missing, ignore
                                    }
                                }
                                if (d.nbt != null) {
                                    applyEntityNBT(ent, d.nbt);
                                }
                                if (d.extra != null && !d.extra.isEmpty()) {
                                    Bukkit.getScheduler().runTaskLater(DungeonInstances.getInstance(), () -> {
                                        applySerializedMap(ent, d.extra);
                                    }, 1L);
                                }
                                ent.setAI(true);

                                ent.setPersistent(true); // ensure they don't despawn
                            }
                        } catch (IllegalArgumentException ignored) {
                            Bukkit.getLogger().warning("Unknown mob type when spawning saved mob: " + d.type);
                        }
                    } catch (IOException ioe) {
                        Bukkit.getLogger().severe("Error reading mob data while spawning: " + ioe.getMessage());
                        Bukkit.getScheduler().cancelTask(taskId[0]);
                    } catch (Exception ex) {
                        Bukkit.getLogger().severe(
                                "Unexpected error in spawnSavedMobs loop for template " + templateName + ": " + ex);
                        // continue with next entry instead of cancelling task
                    }
                }
            }, 0L, 1L);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to open mob data file for " + templateName + ": " + e.getMessage());
        }
    }
}