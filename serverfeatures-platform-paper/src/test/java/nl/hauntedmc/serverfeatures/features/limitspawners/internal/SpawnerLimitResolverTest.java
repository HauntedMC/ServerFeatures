package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpawnerLimitResolverTest {

    @Test
    void choosesHighestMatchingTierAndClampsToHardLimit() {
        Permissible permissible = mock(Permissible.class);
        when(permissible.hasPermission("tier.low")).thenReturn(true);
        when(permissible.hasPermission("tier.high")).thenReturn(true);
        SpawnerLimitResolver resolver = new SpawnerLimitResolver(config());

        SpawnerLimitResolver.PlacementLimit limit = resolver.placementLimit(permissible);

        assertEquals(6, limit.limit());
        assertFalse(limit.hardBypass());
        assertTrue(limit.permits(5));
        assertFalse(limit.permits(6));
    }

    @Test
    void distinguishesSoftAndHardBypassPermissions() {
        SpawnerLimitResolver resolver = new SpawnerLimitResolver(config());
        Permissible soft = mock(Permissible.class);
        when(soft.hasPermission("soft.bypass")).thenReturn(true);
        Permissible hard = mock(Permissible.class);
        when(hard.hasPermission("hard.bypass")).thenReturn(true);

        assertEquals(6, resolver.placementLimit(soft).limit());
        assertFalse(resolver.placementLimit(soft).hardBypass());
        assertTrue(resolver.placementLimit(hard).hardBypass());
        assertTrue(resolver.placementLimit(hard).permits(Integer.MAX_VALUE));
    }

    private static LimitSpawnersConfig config() {
        return new LimitSpawnersConfig(
                32,
                new LimitSpawnersConfig.MobControl(
                        4,
                        16,
                        256,
                        512,
                        200,
                        100,
                        30,
                        30,
                        0,
                        Map.of()
                ),
                new LimitSpawnersConfig.PlacementControl(
                        true,
                        2,
                        6,
                        "soft.bypass",
                        "hard.bypass",
                        List.of(
                                new LimitSpawnersConfig.PermissionTier("tier.low", 3),
                                new LimitSpawnersConfig.PermissionTier("tier.high", 99)
                        )
                ),
                new LimitSpawnersConfig.SpawnerSafety(true, 4, 200, 16, 4, 6),
                new LimitSpawnersConfig.PositionIndex(20),
                true
        );
    }
}
