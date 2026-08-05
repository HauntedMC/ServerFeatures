package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostileEntityClassifierTest {

    @Test
    void enemyInterfaceIsHostileByDefault() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Enemy enemy = mock(Enemy.class);
        when(enemy.getType()).thenReturn(EntityType.ZOMBIE);

        assertTrue(classifier.isHostile(enemy));
    }

    @Test
    void configuredExclusionWinsOverEnemyInterface() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of(EntityType.ZOMBIE));
        Enemy enemy = mock(Enemy.class);
        when(enemy.getType()).thenReturn(EntityType.ZOMBIE);

        assertFalse(classifier.isHostile(enemy));
    }

    @Test
    void configuredInclusionCanMarkNonEnemyEntityType() {
        HostileEntityClassifier classifier = classifier(Set.of(EntityType.COW), Set.of());
        Entity cow = mock(Entity.class);
        when(cow.getType()).thenReturn(EntityType.COW);

        assertTrue(classifier.isHostile(cow));
    }

    @Test
    void nearbyLookupUsesConfiguredClassifier() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Player player = mock(Player.class);
        Enemy enemy = mock(Enemy.class);
        when(enemy.getType()).thenReturn(EntityType.SKELETON);
        when(player.getNearbyEntities(16.0D, 8.0D, 16.0D)).thenReturn(List.of(enemy));

        assertTrue(classifier.hasNearbyHostile(player, 16, 8));
    }

    private static HostileEntityClassifier classifier(
            Set<EntityType> include,
            Set<EntityType> exclude
    ) {
        return new HostileEntityClassifier(new FairPerksSettings.HostileSettings(
                include,
                exclude,
                true,
                false
        ));
    }
}
