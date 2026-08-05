package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void nonEnemyEntityTypesAreNotHostileByDefault() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Entity skeletonHorse = mock(Entity.class);
        Entity nautilus = mock(Entity.class);
        Entity zombieNautilus = mock(Entity.class);
        when(skeletonHorse.getType()).thenReturn(EntityType.SKELETON_HORSE);
        when(nautilus.getType()).thenReturn(EntityType.NAUTILUS);
        when(zombieNautilus.getType()).thenReturn(EntityType.ZOMBIE_NAUTILUS);

        assertFalse(classifier.isHostile(skeletonHorse));
        assertFalse(classifier.isHostile(nautilus));
        assertFalse(classifier.isHostile(zombieNautilus));
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

    @Test
    void nearbyHostileTargetingPlayerIsAggressive() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Player player = mock(Player.class);
        Monster monster = mock(Monster.class);
        when(monster.getType()).thenReturn(EntityType.ZOMBIE);
        when(monster.getTarget()).thenReturn(player);
        when(player.getNearbyEntities(16.0D, 8.0D, 16.0D)).thenReturn(List.of(monster));

        assertTrue(classifier.hasNearbyHostileTargeting(player, 16, 8));
    }

    @Test
    void nearbyHostileWithoutTargetIsNotAggressive() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Player player = mock(Player.class);
        Monster monster = mock(Monster.class);
        when(monster.getType()).thenReturn(EntityType.ZOMBIE);
        when(player.getNearbyEntities(16.0D, 8.0D, 16.0D)).thenReturn(List.of(monster));

        assertFalse(classifier.hasNearbyHostileTargeting(player, 16, 8));
    }

    @Test
    void nearbyHostileTargetingAnotherPlayerIsNotAggressive() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Player player = mock(Player.class);
        Player otherPlayer = mock(Player.class);
        Monster monster = mock(Monster.class);
        when(monster.getType()).thenReturn(EntityType.ZOMBIE);
        when(monster.getTarget()).thenReturn(otherPlayer);
        when(player.getNearbyEntities(16.0D, 8.0D, 16.0D)).thenReturn(List.of(monster));

        assertFalse(classifier.hasNearbyHostileTargeting(player, 16, 8));
    }

    @Test
    void malformedSpawnerMarkerIsTreatedAsUnmarked() {
        HostileEntityClassifier classifier = classifier(Set.of(), Set.of());
        Entity entity = mock(Entity.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BYTE)))
                .thenThrow(new IllegalArgumentException("wrong persistent type"));

        assertFalse(classifier.isExemptSpawnerMob(entity));
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
