package fr.eminiumgames.dungeonsinstances.managers;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import fr.eminiumgames.dungeonsinstances.DungeonInstances;

/**
 * Handles replacement of vanilla mob drops with entries taken from the
 * configured custom loot tables.  Mobs must be tagged with a loot alias via
 * the persistent data container for this mechanism to take effect.
 */
public class LootManager implements Listener {

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity ent = (LivingEntity) event.getEntity();

        String alias = ent.getPersistentDataContainer()
                .get(DungeonManager.getLootAliasKey(), org.bukkit.persistence.PersistentDataType.STRING);
        Bukkit.getLogger().info("LootManager.onEntityDeath: entity=" + ent.getType() + " uuid=" + ent.getUniqueId() + " alias=" + alias);
        boolean usedDefault = false;
        if (alias == null) {
            // try default pool if present
            alias = "default";
            usedDefault = true;
            Bukkit.getLogger().info("LootManager.onEntityDeath: alias was null, falling back to 'default'");
        }

        World world = ent.getWorld();
        String worldName = world.getName();
        String template = DungeonManager.getTemplateFromWorld(worldName);
        if (template == null) {
            // not an instance/edit world we care about
            return;
        }

        DungeonManager.Difficulty diff = DungeonInstances.getInstance().getDungeonManager()
                .getDifficultyForInstance(worldName);
        Bukkit.getLogger().info("LootManager.onEntityDeath: world=" + worldName + " template=" + template + " difficulty=" + diff);
        LootTableManager.LootPool pool = LootTableManager.getInstance().getLootPool(template, diff, alias);
        if (pool == null) {
            Bukkit.getLogger().info("LootManager.onEntityDeath: no pool found for alias=" + alias + (usedDefault ? " (default fallback)" : ""));
            return;
        }

        // clear vanilla drops/exp and spawn our own items so that any
        // custom NBT is properly written to the entity when it appears.
        event.getDrops().clear();
        event.setDroppedExp(0);
        Random rand = new Random();
        int spawned = 0;
        org.bukkit.Location loc = ent.getLocation();
        for (int i = 0; i < pool.iterations; i++) {
            if (pool.loots.isEmpty()) break;
            LootTableManager.LootItem entry = pool.loots.get(rand.nextInt(pool.loots.size()));
            if (entry == null) continue;
            if (rand.nextDouble() < entry.chance) {
                ItemStack stack = LootTableManager.getInstance().buildItem(entry);
                if (stack == null || stack.getType() == Material.AIR) continue;
                Bukkit.getLogger().info("LootManager dropping stack: " + stack + " meta=" + stack.getItemMeta());
                org.bukkit.entity.Item dropped = world.dropItem(loc, stack);
                // try { dropped.setItemStack(stack); } catch (Exception ignore) {}
                // now patch the entity's components using the original entry map
                // applyConfigComponents(dropped, entry.nbt);
                // Bukkit.getScheduler().runTask(DungeonInstances.getInstance(), () -> {
                //     if (dropped != null && !dropped.isDead()) {
                //         applyConfigComponents(dropped, entry.nbt);
                //     }
                // });
                try { dropped.getPersistentDataContainer().set(
                            new org.bukkit.NamespacedKey(DungeonInstances.getInstance(), "dungeon_loot"),
                            org.bukkit.persistence.PersistentDataType.BYTE, (byte)1); } catch (Exception ignore) {}
                spawned++;
            }
        }
        Bukkit.getLogger().info("LootManager.onEntityDeath: generated " + spawned + " items");
    }

    /**
     * Copy the provided NBT map into the `components` tag of the given item
     * entity.  This ensures the Paper entity inspector shows exactly the values
     * defined in the loot configuration rather than the default display name.
     */
    // private void applyConfigComponents(org.bukkit.entity.Item dropped, Map<String,Object> configNbt) {
    //     if (dropped == null || configNbt == null) return;
    //     try {
    //         ItemStack curr = dropped.getItemStack();
    //         if (curr == null) return;

    //         Class<?> craftStack = Class.forName("org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack");
    //         Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
    //         Class<?> nbtCompoundClass = Class.forName("net.minecraft.nbt.CompoundTag");

    //         java.lang.reflect.Method asNMS = craftStack.getMethod("asNMSCopy", ItemStack.class);
    //         Object nmsStack = asNMS.invoke(null, curr);

    //         java.lang.reflect.Method getTag = nmsItemStackClass.getMethod("getTag");
    //         Object tag = getTag.invoke(nmsStack);
    //         if (tag == null) {
    //             tag = nbtCompoundClass.getConstructor().newInstance();
    //             java.lang.reflect.Method setTag = nmsItemStackClass.getMethod("setTag", nbtCompoundClass);
    //             setTag.invoke(nmsStack, tag);
    //         }

    //         // convert config map to a temporary ItemStack so we can extract its
    //         // CompoundTag using the normal Bukkit serializer.
    //         ItemStack temp = org.bukkit.inventory.ItemStack.deserialize(configNbt);
    //         Object tempNms = asNMS.invoke(null, temp);
    //         Object compsTag = getTag.invoke(tempNms);
    //         if (compsTag != null) {
    //             java.lang.reflect.Method put = nbtCompoundClass.getMethod("put", String.class, nbtCompoundClass);
    //             put.invoke(tag, "components", compsTag);
    //             java.lang.reflect.Method asBukkit = craftStack.getMethod("asBukkitCopy", nmsItemStackClass);
    //             ItemStack fixed = (ItemStack) asBukkit.invoke(null, nmsStack);
    //             dropped.setItemStack(fixed);
    //         }
    //     } catch (Exception e) {
    //         Bukkit.getLogger().warning("LootManager: failed to apply config components: " + e.getMessage());
    //     }
    // }
}
