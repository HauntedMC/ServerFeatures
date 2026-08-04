package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerKeyTest {

    @Test
    void ofUsesBlockCoordinatesAndWorldUuid() {
        UUID uid = UUID.randomUUID();
        World world = InterfaceProxy.of(World.class, Map.of("getUID", args -> uid));
        Location location = new Location(world, 10.8D, 64.1D, -3.9D);

        SpawnerKey key = SpawnerKey.of(location);

        assertEquals(uid, key.worldId());
        assertEquals(10, key.x());
        assertEquals(64, key.y());
        assertEquals(-4, key.z());
        assertEquals(0, key.chunkX());
        assertEquals(-1, key.chunkZ());
    }

    @Test
    void serializedFormRoundTrips() {
        SpawnerKey key = new SpawnerKey(UUID.randomUUID(), 1, 2, 3);

        assertEquals(key, SpawnerKey.parse(key.toString()).orElseThrow());
    }

    @Test
    void malformedValuesAreRejectedWithoutThrowing() {
        assertTrue(SpawnerKey.parse("invalid").isEmpty());
        assertTrue(SpawnerKey.parse("not-a-uuid:1:2:3").isEmpty());
        assertTrue(SpawnerKey.parse(UUID.randomUUID() + ":x:2:3").isEmpty());
        assertTrue(SpawnerKey.parse(null).isEmpty());
    }

    @Test
    void distanceUsesThreeDimensionsAndRejectsOtherWorlds() {
        UUID worldId = UUID.randomUUID();
        SpawnerKey first = new SpawnerKey(worldId, 0, 0, 0);
        SpawnerKey second = new SpawnerKey(worldId, 3, 4, 12);

        assertEquals(169L, first.distanceSquared(second));
        assertEquals(
                Long.MAX_VALUE,
                first.distanceSquared(new SpawnerKey(UUID.randomUUID(), 0, 0, 0))
        );
    }
}
