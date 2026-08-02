package nl.hauntedmc.serverfeatures.features.graveyard.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;

public record GraveLocation(
        UUID worldUuid,
        String worldKey,
        double x,
        double y,
        double z,
        float yaw
) {
    public static GraveLocation from(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Grave location must have a world");
        }
        return new GraveLocation(
                world.getUID(),
                world.getKey().asString(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw()
        );
    }

    public Optional<Location> resolve() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null || !world.getKey().asString().equals(worldKey)) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, y, z, yaw, 0.0f));
    }

    public int chunkX() {
        return ((int) Math.floor(x)) >> 4;
    }

    public int chunkZ() {
        return ((int) Math.floor(z)) >> 4;
    }
}
