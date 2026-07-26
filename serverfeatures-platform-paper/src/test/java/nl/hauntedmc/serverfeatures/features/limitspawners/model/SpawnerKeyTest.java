package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpawnerKeyTest {

    @Test
    void ofUsesBlockCoordinatesAndWorldUuid() {
        UUID uid = UUID.randomUUID();
        World world = InterfaceProxy.of(World.class, Map.of("getUID", args -> uid));
        Location loc = new Location(world, 10.8D, 64.1D, -3.9D);

        SpawnerKey key = SpawnerKey.of(loc);

        assertEquals(uid, key.worldId());
        assertEquals(10, key.x());
        assertEquals(64, key.y());
        assertEquals(-4, key.z());
    }

    @Test
    void toStringAndFromStringRoundTrip() {
        SpawnerKey key = new SpawnerKey(UUID.randomUUID(), 1, 2, 3);
        String serialized = key.toString();

        assertEquals(key, SpawnerKey.fromString(serialized));
        assertNull(SpawnerKey.fromString("invalid"));
    }
}

