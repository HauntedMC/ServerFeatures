package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public record TankPosition(UUID worldId, int x, int y, int z) {

    public TankPosition {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static TankPosition of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new TankPosition(
                world.getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public static TankPosition of(Block block) {
        Objects.requireNonNull(block, "block");
        return new TankPosition(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    public TankChunkKey chunkKey() {
        return new TankChunkKey(worldId, x >> 4, z >> 4);
    }
}
