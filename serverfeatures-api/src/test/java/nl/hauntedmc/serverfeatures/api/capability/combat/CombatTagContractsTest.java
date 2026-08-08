package nl.hauntedmc.serverfeatures.api.capability.combat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagContractsTest {

    @Test
    void opponentNormalizesAndValidatesDomainIdentity() {
        UUID id = UUID.randomUUID();
        CombatOpponent opponent = new CombatOpponent(id, " minecraft:zombie ", " ", false);

        assertEquals(id, opponent.uniqueId());
        assertEquals("minecraft:zombie", opponent.entityType());
        assertEquals("minecraft:zombie", opponent.displayName());
        assertFalse(opponent.player());

        assertThrows(NullPointerException.class,
                () -> new CombatOpponent(null, "minecraft:zombie", "Zombie", false));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatOpponent(id, " ", "Zombie", false));
        assertThrows(NullPointerException.class,
                () -> new CombatOpponent(id, "minecraft:zombie", null, false));
    }

    @Test
    void snapshotClampsNegativeRemainingAndPreservesStableValues() {
        UUID playerId = UUID.randomUUID();
        CombatOpponent opponent = new CombatOpponent(UUID.randomUUID(), "minecraft:player", "Enemy", true);
        Instant taggedAt = Instant.parse("2026-08-08T10:00:00Z");
        Instant expiresAt = taggedAt.plusSeconds(15);
        CombatTagSnapshot snapshot = new CombatTagSnapshot(
                playerId,
                opponent,
                CombatTagReason.MELEE,
                taggedAt,
                expiresAt,
                Duration.ofSeconds(-1)
        );

        assertEquals(playerId, snapshot.playerId());
        assertEquals(opponent, snapshot.opponent());
        assertEquals(CombatTagReason.MELEE, snapshot.reason());
        assertEquals(taggedAt, snapshot.taggedAt());
        assertEquals(expiresAt, snapshot.expiresAt());
        assertEquals(Duration.ZERO, snapshot.remaining());
        assertTrue(opponent.player());

        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(null, opponent, CombatTagReason.MELEE,
                        taggedAt, expiresAt, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(playerId, null, CombatTagReason.MELEE,
                        taggedAt, expiresAt, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(playerId, opponent, null,
                        taggedAt, expiresAt, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(playerId, opponent, CombatTagReason.MELEE,
                        null, expiresAt, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(playerId, opponent, CombatTagReason.MELEE,
                        taggedAt, null, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CombatTagSnapshot(playerId, opponent, CombatTagReason.MELEE,
                        taggedAt, expiresAt, null));
    }

    @Test
    void reasonEnumIsExplicitAndStable() {
        assertEquals(11, CombatTagReason.values().length);
        assertEquals(CombatTagReason.EXTERNAL, CombatTagReason.valueOf("EXTERNAL"));
    }
}
