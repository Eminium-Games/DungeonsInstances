package fr.eminiumgames.dungeonsinstances.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
        // diagnostic: print raw entry map
        // Bukkit.getLogger().info("buildItem called for item=" + entry.item + " nbt=" + entry.nbt);
        if (entry.nbt != null && entry.nbt.isEmpty()) {
            // Bukkit.getLogger().info("buildItem: entry.nbt is empty for " + entry.item);
        }

        String matName = entry.item.toUpperCase();
        Material mat = Material.matchMaterial(matName);
        if (mat == null && matName.contains(":")) {
            // try without namespace prefix
            String after = matName.substring(matName.indexOf(":") + 1);
            mat = Material.matchMaterial(after);
        }
        if (mat == null) {
            Bukkit.getLogger().warning("Unknown material in loot config: " + entry.item);
            mat = Material.STONE;
        }
        ItemStack stack = new ItemStack(mat, Math.max(1, entry.count));
        // compute a component name regardless of NBT content so item entity can
        // always carry something human-readable. prioritize explicit map keys,
        // then fall back to a nicely-formatted material name. we keep track of
        // whether an explicit name was provided so we don't accidentally
        // overwrite a custom value later.
        boolean explicitName = false;
        String compName = null;
        if (entry.nbt != null) {
            // top-level names
            if (entry.nbt.containsKey("item_name")) {
                Object n = entry.nbt.get("item_name");
                if (n instanceof String) {
                    compName = (String) n;
                    explicitName = true;
                }
            }
            if (!explicitName && entry.nbt.containsKey("displayName")) {
                Object n = entry.nbt.get("displayName");
                if (n instanceof String) {
                    compName = (String) n;
                    explicitName = true;
                }
            }
            // also inspect nested components map for the same keys, which some
            // configurations use
            if (!explicitName && entry.nbt.containsKey("components")) {
                Object compsObj = entry.nbt.get("components");
                if (compsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> comps = (Map<String, Object>) compsObj;
                    if (comps.containsKey("minecraft:item_name")) {
                        Object n = comps.get("minecraft:item_name");
                        if (n instanceof String) {
                            compName = (String) n;
                            explicitName = true;
                        }
                    }
                    if (!explicitName && comps.containsKey("item_name")) {
                        Object n = comps.get("item_name");
                        if (n instanceof String) {
                            compName = (String) n;
                            explicitName = true;
                        }
                    }
                    if (!explicitName && comps.containsKey("displayName")) {
                        Object n = comps.get("displayName");
                        if (n instanceof String) {
                            compName = (String) n;
                            explicitName = true;
                        }
                    }
                }
            }
        }
        if (compName == null) {
            // no custom name was given; use a human-friendly version of the
            // material's name ("GOLD_INGOT" -> "Gold Ingot").
            compName = prettifyMaterialName(stack.getType());
        }

        if (entry.nbt != null && !entry.nbt.isEmpty()) {
            // Bukkit.getLogger().info("buildItem processing nbt keys=" + entry.nbt.keySet());
        }

        // ensure the components tag exists in the config map so file reflects
        // what we actually spawn. instead of putting only a custom_name key we
        // copy the entire nbt map so operators can inspect and modify it
        // directly using the in-game entity inspector.
        if (entry.nbt == null) {
            entry.nbt = new HashMap<>();
        }
        if (!entry.nbt.containsKey("components")) {
            Map<String, Object> comps = new HashMap<>(entry.nbt);
            // make sure at least a name is present. only insert a custom name if
            // the configuration actually specified one; otherwise we want the
            // default game behaviour to show the item's normal display name.
            if (!comps.containsKey("minecraft:custom_name") && explicitName) {
                comps.put("minecraft:custom_name", compName);
            }
            entry.nbt.put("components", comps);
            // Bukkit.getLogger().info("LootTableManager: injecting full components for " + entry.item + " -> " + comps);
            save();
        }

        // set display name on meta unconditionally so the dropped item shows the
        // name immediately and Paper will populate components automatically.
        ItemMeta baseMeta = stack.getItemMeta();
        if (baseMeta == null) {
            baseMeta = Bukkit.getItemFactory().getItemMeta(stack.getType());
        }
        if (baseMeta != null && !baseMeta.hasItemName()) {
            baseMeta.setItemName(compName);
            stack.setItemMeta(baseMeta);
        }

        // try to honor arbitrary NBT via Bukkit's serializer; this will also
        // apply the components tag we just added.
        try {
            ItemStack des = org.bukkit.inventory.ItemStack.deserialize(entry.nbt);
            if (des != null) {
                des.setAmount(stack.getAmount());
                stack = des;
            }
        } catch (Exception ignore) {
            // ignore and continue with manual meta below
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && entry.nbt != null) {
            // Bukkit.getLogger()
            //         .info("buildItem applying meta for " + entry.item + " with nbt keys=" + entry.nbt.keySet());
            if (entry.nbt.containsKey("components") && entry.nbt.get("components") instanceof Map) {
                // Bukkit.getLogger().info("buildItem processing components for " + entry.item);
                Map<String, Object> components = (Map<String, Object>) entry.nbt.get("components");
                if (components.containsKey("minecraft:item_name")) {
                    // Bukkit.getLogger().info("buildItem found minecraft:item_name in components for " + entry.item);
                    Object name = components.get("minecraft:item_name");
                    // Bukkit.getLogger().info("buildItem setting custom name from minecraft:item_name: " + name);
                    meta.setItemName((String) name);

                }
            }
            if (entry.nbt.containsKey("lore")) {
                Object lore = entry.nbt.get("lore");
                if (lore instanceof List) {
                    // noinspection unchecked
                    meta.setLore((List<String>) lore);
                }
            }
            stack.setItemMeta(meta);
        }
        // if no explicit name was given via NBT, enforce our human-readable
        // default; the earlier meta manipulations may have left a blank or
        // placeholder value after the deserializer ran.
        // if (!explicitName && meta != null) {
        meta.setItemName(compName);
        stack.setItemMeta(meta);
        // }
        // Bukkit.getLogger().info("buildItem result stack=" + stack + " serialized=" + stack.serialize());
        return stack;
    }

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
