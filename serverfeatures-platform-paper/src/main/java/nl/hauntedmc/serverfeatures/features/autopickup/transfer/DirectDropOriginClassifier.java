package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import nl.hauntedmc.serverfeatures.features.autopickup.model.DropScope;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Item;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Conservatively attributes event item entities to the directly broken block.
 */
public final class DirectDropOriginClassifier {

    public boolean eligible(BlockState brokenState, Item item, DropScope scope) {
        Objects.requireNonNull(brokenState, "brokenState");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(scope, "scope");
        if (scope == DropScope.EVENT_ALL) {
            return true;
        }
        BlockKey itemOrigin = BlockKey.from(item.getLocation());
        return allowedOrigins(brokenState).contains(itemOrigin);
    }

    Set<BlockKey> allowedOrigins(BlockState brokenState) {
        Location origin = brokenState.getLocation();
        Set<BlockKey> allowed = new HashSet<>();
        allowed.add(BlockKey.from(origin));

        BlockData blockData = brokenState.getBlockData();
        if (blockData instanceof Bed bed) {
            BlockFace facing = bed.getFacing();
            int direction = bed.getPart() == Bed.Part.FOOT ? 1 : -1;
            allowed.add(BlockKey.from(origin.clone().add(
                    facing.getModX() * direction,
                    0,
                    facing.getModZ() * direction
            )));
        } else if (blockData instanceof Bisected bisected) {
            int yOffset = bisected.getHalf() == Bisected.Half.BOTTOM ? 1 : -1;
            allowed.add(BlockKey.from(origin.clone().add(0, yOffset, 0)));
        }
        return Set.copyOf(allowed);
    }

    record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey from(Location location) {
            if (location.getWorld() == null) {
                throw new IllegalArgumentException("Drop location has no world");
            }
            return new BlockKey(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }
    }
}
