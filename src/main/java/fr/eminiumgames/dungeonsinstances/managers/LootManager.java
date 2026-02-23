package fr.eminiumgames.dungeonsinstances.managers;

import java.util.Random;

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
        if (alias == null) alias = "default";

        World world = ent.getWorld();
        String worldName = world.getName();
        String template = DungeonManager.getTemplateFromWorld(worldName);
        if (template == null) {
            // not an instance/edit world we care about
            return;
        }

        DungeonManager.Difficulty diff = DungeonInstances.getInstance().getDungeonManager()
                .getDifficultyForInstance(worldName);
        LootTableManager.LootPool pool = LootTableManager.getInstance().getLootPool(template, diff, alias);
        if (pool == null || pool.loots.isEmpty()) return;

        // remove vanilla drops/exp; we'll spawn our own items with the
        // configured NBT so the entity inspector reflects the loot data.
        event.getDrops().clear();
        event.setDroppedExp(0);

        Random rand = new Random();
        org.bukkit.Location loc = ent.getLocation();
        for (int i = 0; i < pool.iterations; i++) {
            LootTableManager.LootItem entry = pool.loots.get(rand.nextInt(pool.loots.size()));
            if (entry == null || rand.nextDouble() >= entry.chance) continue;
            ItemStack stack = LootTableManager.getInstance().buildItem(entry);
            if (stack == null || stack.getType() == Material.AIR) continue;
            org.bukkit.entity.Item dropped = world.dropItem(loc, stack);
            try {
                dropped.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(DungeonInstances.getInstance(), "dungeon_loot"),
                        org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            } catch (Exception ignore) {}
        }

    }
}
