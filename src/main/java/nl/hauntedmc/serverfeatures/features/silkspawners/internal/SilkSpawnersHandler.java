package nl.hauntedmc.serverfeatures.features.silkspawners.internal;

import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.silkspawners.SilkSpawners;
import nl.hauntedmc.serverfeatures.features.silkspawners.util.ItemUtils;
import nl.hauntedmc.serverfeatures.features.silkspawners.util.LegacyUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SilkSpawnersHandler {

    private final SilkSpawners feature;
    private final List<String> allowed_spawners;

    public SilkSpawnersHandler(SilkSpawners feature) {
        this.feature = feature;
        allowed_spawners = CastUtils.safeCastToList(feature.getConfigHandler()
                .getSetting("allowed_spawner_types"), String.class);
    }

    public void handleSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;

        if (!player.hasPermission("serverfeatures.feature.silkspawners.mine")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_rank")
                            .forAudience(player)
                            .with("rank", "&6Legend")
                            .build()
            );
            event.setCancelled(true);
            return;
        }

        CreatureSpawner minedSpawner = (CreatureSpawner) block.getState();
        EntityType type = minedSpawner.getSpawnedType();
        String typeName;

        if (type != null) {
            typeName = type.name();
        } else {
            event.setCancelled(true);
            return;
        }


        // check allowed types list (empty = no restriction)
        if (!allowed_spawners.isEmpty() && !allowed_spawners.contains(typeName)) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.not_allowed_type")
                            .with("type", typeName)
                            .forAudience(player)
                            .build()
            );
            event.setCancelled(true);
            return;
        }

        // check space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.no_space")
                            .forAudience(player)
                            .build()
            );
            event.setCancelled(true);
            return;
        }

        event.setExpToDrop(0);

        // build the spawner item with preserved entity type
        ItemStack spawnerItem = ItemUtils.createSpawnerItem(type);

        // give the item
        player.getInventory().addItem(spawnerItem);

        // confirm success
        player.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("silkspawners.success")
                        .with("type", typeName)
                        .forAudience(player)
                        .build()
        );
    }

    /**
     * Call from your BlockPlaceEvent listener to restore the spawner’s mob type,
     * enforcing permission and whitelist.
     */
    public void handleSpawnerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.silkspawners.place")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_rank")
                            .forAudience(player)
                            .with("rank", "&6Legend")
                            .build()
            );
            event.setCancelled(true);
            return;
        }

        // extract the stored BlockStateMeta from the item in hand
        ItemStack item = event.getItemInHand();
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof CreatureSpawner csMeta)) return;

        // read the type they’re trying to place
        EntityType type = csMeta.getSpawnedType();
        String typeName;

        CreatureSpawner placed = (CreatureSpawner) block.getState();

        if (type == null) {
            // Troubles, we might be dealing with a legacy spawner!
            String dumped = item.getItemMeta().getAsString();
            Optional<String> maybeMob = LegacyUtils.extractLegacyMobType(dumped);
            if (maybeMob.isPresent()) {
                //Create new good spawner item
                typeName = maybeMob.get();  // e.g. "ZOMBIE"
                type = EntityType.valueOf(typeName);
                ItemStack newPlacedBlock = ItemUtils.createSpawnerItem(type);
                BlockStateMeta newMeta = (BlockStateMeta) newPlacedBlock.getItemMeta();
                CreatureSpawner newCsm = (CreatureSpawner) newMeta.getBlockState();
                placed.setBlockData(newCsm.getBlockData());
            } else {
                feature.getLogger().warning("Unknown legacy spawner detected");
                event.setCancelled(true);
                return;
            }
        } else {
            typeName = type.name();
        }

        // whitelist check (empty list = allow all)
        if (!allowed_spawners.isEmpty() && !allowed_spawners.contains(typeName)) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("silkspawners.not_allowed_type")
                            .with("type", typeName)
                            .forAudience(player)
                            .build()
            );
            event.setCancelled(true);
            return;
        }

        // OK—apply it to the newly placed block
        placed.setSpawnedType(type);
        placed.update();
    }
}
