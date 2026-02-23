package fr.eminiumgames.dungeonsinstances.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import fr.eminiumgames.dungeonsinstances.managers.DungeonManager.Difficulty;

/**
 * Singleton responsible for loading and querying custom loot tables. Tables
 * are stored in a JSON file under the plugin data folder and are organised by
 * template name & difficulty level.
 *
 * <p>The configuration format is purposely simple. Example structure:
 * <pre>{@code
 * {
 * "manaria": {
 * "NORMAL": {
 * "default": {
 * "iterations": 3,
 * "loots": [
 * {"item":"diamond_sword","nbt":{},"count":1,"chance":0.1},
 * {"item":"iron_nugget","nbt":{"item_name":"Ecu"},"count":1,"chance":0.4}
 * ]
 * }
 * }
 * }
 * }
 * }</pre>
 *
 * <p>The plugin will automatically create an empty file if none exists so
 * server operators can start filling it in. A loader method is exposed so
 * administrators can reload the configuration at runtime.
 */
public class LootTableManager {

    private static LootTableManager instance = new LootTableManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File configFile;

    /**
     * Outer map: template -> difficulty -> alias -> pool
     */
    private final Map<String, Map<Difficulty, Map<String, LootPool>>> tables = new HashMap<>();

    private LootTableManager() {
        configFile = new File("plugins/DungeonInstances/lootTables.json");
        load();
    }

    public static LootTableManager getInstance() {
        return instance;
    }

    /**
     * Load the JSON file into memory. Existing data is discarded.
     */
    public synchronized void load() {
        tables.clear();
        if (!configFile.exists()) {
            // create an empty placeholder so admins can edit it later
            try {
                if (configFile.getParentFile() != null) {
                    configFile.getParentFile().mkdirs();
                }
                try (FileWriter fw = new FileWriter(configFile)) {
                    fw.write("{}\n");
                }
            } catch (IOException e) {
                Bukkit.getLogger().severe("Failed to create lootTables.json: " + e.getMessage());
            }
            return;
        }

        try (FileReader fr = new FileReader(configFile)) {
            Type rawType = new TypeToken<Map<String, Map<String, Map<String, LootPool>>>>() {
            }.getType();
            Map<String, Map<String, Map<String, LootPool>>> raw = gson.fromJson(fr, rawType);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, Map<String, Map<String, LootPool>>> tplEntry : raw.entrySet()) {
                String template = tplEntry.getKey();
                Map<String, Map<String, LootPool>> diffMap = tplEntry.getValue();
                Map<Difficulty, Map<String, LootPool>> temp = new EnumMap<>(Difficulty.class);
                for (Map.Entry<String, Map<String, LootPool>> diffEntry : diffMap.entrySet()) {
                    Difficulty d = Difficulty.fromString(diffEntry.getKey());
                    temp.put(d, diffEntry.getValue());
                }
                tables.put(template, temp);
            }
            // after loading, ensure any default pool that's still empty gets example
            boolean changed = false;
            for (Map.Entry<String, Map<Difficulty, Map<String, LootPool>>> tpl : tables.entrySet()) {
                for (Difficulty d : Difficulty.values()) {
                    Map<String, LootPool> byDiff = tpl.getValue().get(d);
                    if (byDiff != null) {
                        LootPool pool = byDiff.get("default");
                        if (pool != null && pool.loots.isEmpty()) {
                            populateExampleLoot(pool, d);
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                save();
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Error reading lootTables.json: " + e.getMessage());
        }
    }

    /**
     * Ensure that the given template has at least an empty pool named "default"
     * for every defined difficulty. This is invoked at plugin startup and
     * when a world/template is saved so that server operators always see a
     * minimal structure in the JSON file.
     */
    public synchronized void ensureTemplateHasAllDifficulties(String templateName) {
        if (templateName == null)
            return;
        Map<Difficulty, Map<String, LootPool>> temp = tables.computeIfAbsent(templateName,
                k -> new EnumMap<>(Difficulty.class));
        for (Difficulty d : Difficulty.values()) {
            Map<String, LootPool> byDiff = temp.computeIfAbsent(d, k -> new HashMap<>());
            LootPool defaultPool = byDiff.get("default");
            if (defaultPool == null) {
                defaultPool = new LootPool();
                byDiff.put("default", defaultPool);
            }
            // if the pool is empty, insert starter items based on difficulty
            if (defaultPool.loots.isEmpty()) {
                populateExampleLoot(defaultPool, d);
            }
        }
        save();
    }

    /**
     * Persist the current `tables` map to disk in the same JSON structure
     * used by {@link #load()}.
     */
    private synchronized void save() {
        try (FileWriter fw = new FileWriter(configFile)) {
            // convert enum keys back to strings
            Map<String, Map<String, Map<String, LootPool>>> raw = new HashMap<>();
            for (Map.Entry<String, Map<Difficulty, Map<String, LootPool>>> tplEntry : tables.entrySet()) {
                String tpl = tplEntry.getKey();
                Map<Difficulty, Map<String, LootPool>> diffMap = tplEntry.getValue();
                Map<String, Map<String, LootPool>> rDiff = new HashMap<>();
                for (Map.Entry<Difficulty, Map<String, LootPool>> dentry : diffMap.entrySet()) {
                    rDiff.put(dentry.getKey().name(), dentry.getValue());
                }
                raw.put(tpl, rDiff);
            }
            gson.toJson(raw, fw);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to write lootTables.json: " + e.getMessage());
        }
    }

    /**
     * Return a specific loot pool if it exists, otherwise null.
     */
    public LootPool getLootPool(String templateName, Difficulty diff, String alias) {
        Bukkit.getLogger().info(
                "LootTableManager.getLootPool: template=" + templateName + " difficulty=" + diff + " alias=" + alias);
        if (templateName == null || diff == null || alias == null) {
            return null;
        }
        Map<Difficulty, Map<String, LootPool>> temp = tables.get(templateName);
        if (temp == null) {
            return null;
        }
        Map<String, LootPool> byDiff = temp.get(diff);
        if (byDiff == null) {
            return null;
        }
        return byDiff.get(alias);
    }

    /**
     * Return a generated list of drops from a given pool. This does not modify
     * the pool itself; repeated calls may return different results.
     */
    /**
     * Helper used by ensureTemplateHasAllDifficulties to prefill a newly-created
     * default pool. The values mimic the example shown in the README and are
     * independent of the template name; only difficulty matters.
     */
    private void populateExampleLoot(LootPool pool, Difficulty d) {
        if (pool == null)
            return;
        switch (d) {
            case BEGINNER -> {
                pool.iterations = 2;
                pool.loots.add(createItem("emerald", 1, 0.2));
                pool.loots.add(createItem("gold_nugget", 2, 0.5));
            }
            case NORMAL -> {
                pool.iterations = 3;
                pool.loots.add(createItem("emerald", 1, 0.4));
                pool.loots.add(createItem("gold_ingot", 1, 0.25));
                pool.loots.add(createItem("iron_nugget", 3, 0.6));
            }
            case HEROIC -> {
                pool.iterations = 4;
                pool.loots.add(createItem("diamond", 1, 0.05));
                pool.loots.add(createItem("gold_ingot", 2, 0.4));
                pool.loots.add(createItem("iron_ingot", 5, 0.8));
            }
            case MYTHIC -> {
                pool.iterations = 5;
                pool.loots.add(createItem("nether_star", 1, 0.01));
                pool.loots.add(createItem("diamond", 2, 0.2));
                pool.loots.add(createItem("gold_ingot", 4, 0.5));
            }
        }
    }

    /**
     * Convert a `Material` enum name into a nicer human-readable string.
     * For example, `GOLD_INGOT` becomes `Gold Ingot`.
     */
    private static String prettifyMaterialName(Material mat) {
        if (mat == null)
            return "";
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : name.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                cap = c == ' ';
            }
        }
        return sb.toString();
    }

    private LootItem createItem(String mat, int count, double chance) {
        LootItem it = new LootItem();
        it.item = "minecraft:" + mat;
        it.count = count;
        it.chance = chance;
        it.nbt = new HashMap<>();
        return it;
    }

    public List<ItemStack> roll(LootPool pool) {
        List<ItemStack> drops = new ArrayList<>();
        if (pool == null || pool.loots == null || pool.loots.isEmpty()) {
            return drops;
        }
        Random rand = new Random();
        for (int i = 0; i < pool.iterations; i++) {
            LootItem entry = pool.loots.get(rand.nextInt(pool.loots.size()));
            if (entry == null)
                continue;
            if (rand.nextDouble() < entry.chance) {
                ItemStack item = buildItem(entry);
                if (item != null && item.getType() != Material.AIR) {
                    drops.add(item);
                }
            }
        }
        return drops;
    }

    ItemStack buildItem(LootItem entry) {
        if (entry == null || entry.item == null)
            return null;

        Material mat = resolveMaterial(entry.item);
        if (mat == null) {
            Bukkit.getLogger().warning("Unknown material in loot config: " + entry.item);
            mat = Material.STONE;
        }

        ItemStack stack = new ItemStack(mat, Math.max(1, entry.count));
        String compName = extractDisplayName(entry);
        if (compName == null) {
            compName = prettifyMaterialName(mat);
        }

        ensureComponentsInNBT(entry, compName);
        applyItemMeta(stack, entry, compName);

        return stack;
    }

    private Material resolveMaterial(String item) {
        Material mat = Material.matchMaterial(item.toUpperCase());
        if (mat == null && item.contains(":")) {
            mat = Material.matchMaterial(item.substring(item.indexOf(":") + 1).toUpperCase());
        }
        return mat;
    }

    private String extractDisplayName(LootItem entry) {
        if (entry.nbt == null) return null;
        
        String name = findInMap(entry.nbt, "item_name", "displayName");
        if (name != null) return name;
        
        if (entry.nbt.get("components") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> comps = (Map<String, Object>) entry.nbt.get("components");
            return findInMap(comps, "minecraft:item_name", "item_name", "displayName");
        }
        return null;
    }

    private String findInMap(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof String) return (String) val;
        }
        return null;
    }

    private void ensureComponentsInNBT(LootItem entry, String displayName) {
        if (entry.nbt == null) entry.nbt = new HashMap<>();
        if (!entry.nbt.containsKey("components")) {
            Map<String, Object> comps = new HashMap<>(entry.nbt);
            if (!comps.containsKey("minecraft:custom_name") && extractDisplayName(entry) != null) {
                comps.put("minecraft:custom_name", displayName);
            }
            entry.nbt.put("components", comps);
            save();
        }
    }

    /**
     * Use NMS to apply the provided NBT map directly onto the given stack.
     * The map may contain a `components` submap (or any other tags); they are
     * merged verbatim into the NMS ItemStack's tag so the game processes them
     * exactly as if they were read from an actual ItemStack nbt blob.
     */
    private ItemStack applyNmsComponents(ItemStack stack, Map<String, Object> nbtMap) {
        if (nbtMap == null || nbtMap.isEmpty()) return stack;
        try {
            // reflection to avoid compile-time NMS dependency
            Class<?> craftStackClass = Class.forName("org.bukkit.craftbukkit.v1_21_R2.inventory.CraftItemStack");
            Method toNms = craftStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nms = toNms.invoke(null, stack);

            Class<?> nmsStackClass = nms.getClass();
            Method hasTag = nmsStackClass.getMethod("hasTag");
            Method getTag = nmsStackClass.getMethod("getTag");
            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            Object tag = (Boolean) hasTag.invoke(nms) ? getTag.invoke(nms) : compoundTagClass.getConstructor().newInstance();

            Class<?> tagClass = Class.forName("net.minecraft.nbt.Tag");
            Method aMethod = tagClass.getMethod("a", Object.class);

            // merge provided map entries into tag
            for (Map.Entry<String, Object> e : nbtMap.entrySet()) {
                Object valueTag = aMethod.invoke(null, e.getValue());
                Method put = tag.getClass().getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"));
                put.invoke(tag, e.getKey(), valueTag);
            }

            Method setTag = nmsStackClass.getMethod("setTag", compoundTagClass);
            setTag.invoke(nms, tag);

            Method toBukkit = craftStackClass.getMethod("asBukkitCopy", nmsStackClass);
            stack = (ItemStack) toBukkit.invoke(null, nms);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to apply NMS components: " + e.getMessage());
        }
        return stack;
    }

    private ItemStack applyItemMeta(ItemStack stack, LootItem entry, String compName) {
        // first, push the raw NBT through NMS so every component key is
        // interpreted exactly as the game would.  this completely replaces
        // the earlier Bukkit.deserialize approach.
        if (entry.nbt != null) {
            stack = applyNmsComponents(stack, entry.nbt);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (entry.nbt != null && entry.nbt.get("lore") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> lore = (List<String>) entry.nbt.get("lore");
                meta.setLore(lore);
            }
            meta.setItemName(compName);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // remove old manual applyComponents; NMS now covers all cases

    /**
     * A single pool of potential loot entries.
     */
    public static class LootPool {
        public int iterations = 1;
        public List<LootItem> loots = new ArrayList<>();
    }

    /**
     * A single entry inside a pool.
     */
    public static class LootItem {
        public String item;
        public Map<String, Object> nbt;
        public int count = 1;
        public double chance = 1.0;
    }
}
