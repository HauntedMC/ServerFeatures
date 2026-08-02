package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SpawnerKey(UUID worldId, int x, int y, int z) {

    public SpawnerKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static SpawnerKey of(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new SpawnerKey(
                world.getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public @NotNull String toString() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    public static Optional<SpawnerKey> parse(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }

        String[] parts = serialized.split(":", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }

        try {
            return Optional.of(new SpawnerKey(
                    UUID.fromString(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Compatibility helper for callers that still use the old nullable parser.
     */
    public static SpawnerKey fromString(String serialized) {
        return parse(serialized).orElse(null);
    }
}
