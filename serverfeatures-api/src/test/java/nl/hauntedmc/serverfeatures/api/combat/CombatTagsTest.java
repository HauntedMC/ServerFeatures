package nl.hauntedmc.serverfeatures.api.combat;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatTagsTest {

    private CombatTagApi active;

    @AfterEach
    void tearDown() {
        if (active != null) {
            CombatTags.shutdown(active);
        }
    }

    @Test
    void returnsSafeNoopWhenFeatureIsUnavailable() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertFalse(CombatTags.service().isTagged(player));
        assertEquals(Optional.empty(), CombatTags.service().getTag(player.getUniqueId()));
    }

    @Test
    void exposesAndConditionallyRemovesBootstrappedService() {
        CombatTagApi first = mock(CombatTagApi.class);
        CombatTagApi second = mock(CombatTagApi.class);

        CombatTags.bootstrap(first);
        CombatTags.bootstrap(second);
        active = second;

        CombatTags.shutdown(first);

        assertSame(second, CombatTags.service());
    }
}
