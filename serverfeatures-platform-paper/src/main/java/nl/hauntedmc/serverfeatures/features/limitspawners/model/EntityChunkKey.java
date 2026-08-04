package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public record EntityChunkKey(UUID worldId, int x, int z) {

    public EntityChunkKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static EntityChunkKey of(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return new EntityChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    public static EntityChunkKey of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new EntityChunkKey(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
