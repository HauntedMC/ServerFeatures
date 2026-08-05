package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public record TankChunkKey(UUID worldId, int x, int z) {

    public TankChunkKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static TankChunkKey of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new TankChunkKey(
                world.getUID(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4
        );
    }

    public static TankChunkKey of(Block block) {
        Objects.requireNonNull(block, "block");
        return new TankChunkKey(
                block.getWorld().getUID(),
                block.getX() >> 4,
                block.getZ() >> 4
        );
    }
}
