package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import org.bukkit.block.CreatureSpawner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnerSafetyPolicyTest {

    @Test
    void clampsOnlyUnsafeValuesAndUpdatesOnce() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        when(spawner.getSpawnCount()).thenReturn(10);
        when(spawner.getMinSpawnDelay()).thenReturn(20);
        when(spawner.getMaxSpawnDelay()).thenReturn(100);
        when(spawner.getRequiredPlayerRange()).thenReturn(64);
        when(spawner.getSpawnRange()).thenReturn(12);
        when(spawner.getMaxNearbyEntities()).thenReturn(50);
        SpawnerSafetyPolicy policy = new SpawnerSafetyPolicy(
                new LimitSpawnersConfig.SpawnerSafety(true, 4, 200, 16, 4, 6)
        );

        assertTrue(policy.apply(spawner));

        verify(spawner).setSpawnCount(4);
        verify(spawner).setMaxSpawnDelay(200);
        verify(spawner).setMinSpawnDelay(200);
        verify(spawner).setRequiredPlayerRange(16);
        verify(spawner).setSpawnRange(4);
        verify(spawner).setMaxNearbyEntities(6);
        verify(spawner).update(true, false);
    }

    @Test
    void leavesSafeValuesAndDisabledPolicyUntouched() {
        CreatureSpawner safe = mock(CreatureSpawner.class);
        when(safe.getSpawnCount()).thenReturn(2);
        when(safe.getMinSpawnDelay()).thenReturn(200);
        when(safe.getMaxSpawnDelay()).thenReturn(800);
        when(safe.getRequiredPlayerRange()).thenReturn(16);
        when(safe.getSpawnRange()).thenReturn(4);
        when(safe.getMaxNearbyEntities()).thenReturn(6);
        SpawnerSafetyPolicy enabled = new SpawnerSafetyPolicy(
                new LimitSpawnersConfig.SpawnerSafety(true, 4, 200, 16, 4, 6)
        );

        assertFalse(enabled.apply(safe));
        verify(safe, never()).update(true, false);

        CreatureSpawner disabledSpawner = mock(CreatureSpawner.class);
        SpawnerSafetyPolicy disabled = new SpawnerSafetyPolicy(
                new LimitSpawnersConfig.SpawnerSafety(false, 4, 200, 16, 4, 6)
        );

        assertFalse(disabled.apply(disabledSpawner));
        verify(disabledSpawner, never()).getSpawnCount();
    }
}
