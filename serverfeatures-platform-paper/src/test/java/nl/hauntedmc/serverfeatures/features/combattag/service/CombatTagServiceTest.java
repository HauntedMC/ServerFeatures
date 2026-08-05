package nl.hauntedmc.serverfeatures.features.combattag.service;

import nl.hauntedmc.serverfeatures.api.combat.CombatOpponent;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.features.combattag.CombatTag;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatTagServiceTest {

    @Test
    void tagAndRetagReplaceOpponentAndResetTheTimer() {
        Fixture fixture = fixture(false);
        CombatOpponent first = opponent("zombie", EntityType.ZOMBIE);
        CombatOpponent second = opponent("skeleton", EntityType.SKELETON);

        assertEquals(
                CombatTagResult.TAGGED,
                fixture.service().tagIncoming(
                        fixture.player(),
                        first,
                        first.uniqueId(),
                        CombatTagReason.MELEE
                )
        );
        fixture.nanoTime().addAndGet(5_000_000_000L);
        assertEquals(
                CombatTagResult.RETAGGED,
                fixture.service().tagIncoming(
                        fixture.player(),
                        second,
                        second.uniqueId(),
                        CombatTagReason.PROJECTILE
                )
        );

        var snapshot = fixture.service().getTag(fixture.player()).orElseThrow();
        assertEquals(second, snapshot.opponent());
        assertEquals(CombatTagReason.PROJECTILE, snapshot.reason());
        assertEquals(15L, snapshot.remaining().toSeconds());
    }

    @Test
    void outgoingRetagKeepsTheLastIncomingAttackerForLogoutAttribution() {
        Fixture fixture = fixture(false);
        CombatOpponent attacker = opponent("attacker", EntityType.ZOMBIE);
        CombatOpponent attacked = opponent("attacked", EntityType.SKELETON);

        fixture.service().tagIncoming(
                fixture.player(),
                attacker,
                attacker.uniqueId(),
                CombatTagReason.MELEE
        );
        fixture.service().tagOutgoing(
                fixture.player(),
                attacked,
                attacked.uniqueId(),
                CombatTagReason.MELEE
        );

        var visible = fixture.service().getTag(fixture.player()).orElseThrow();
        var stored = fixture.service().snapshotForReload().get(fixture.player().getUniqueId());
        assertEquals(attacked, visible.opponent());
        assertEquals(attacker, stored.logoutOpponent());
        assertEquals(attacker.uniqueId(), stored.logoutDamageSourceId());
    }

    @Test
    void expiredTagIsNotReportedByTheApi() {
        Fixture fixture = fixture(false);
        CombatOpponent opponent = opponent("zombie", EntityType.ZOMBIE);
        fixture.service().tagIncoming(
                fixture.player(),
                opponent,
                opponent.uniqueId(),
                CombatTagReason.MELEE
        );

        fixture.nanoTime().addAndGet(16_000_000_000L);

        assertFalse(fixture.service().isTagged(fixture.player()));
    }

    @Test
    void bypassPermissionPreventsTagging() {
        Fixture fixture = fixture(true);
        CombatOpponent opponent = opponent("zombie", EntityType.ZOMBIE);

        assertEquals(
                CombatTagResult.BYPASSED,
                fixture.service().tagIncoming(
                        fixture.player(),
                        opponent,
                        opponent.uniqueId(),
                        CombatTagReason.MELEE
                )
        );
        assertFalse(fixture.service().isTagged(fixture.player()));
    }

    @Test
    void reloadSnapshotPreservesOnlyTheRemainingDuration() {
        Fixture fixture = fixture(false);
        CombatOpponent opponent = opponent("zombie", EntityType.ZOMBIE);
        fixture.service().tagIncoming(
                fixture.player(),
                opponent,
                opponent.uniqueId(),
                CombatTagReason.MELEE
        );
        fixture.nanoTime().addAndGet(4_000_000_000L);

        var snapshot = fixture.service().snapshotForReload();

        assertTrue(snapshot.containsKey(fixture.player().getUniqueId()));
        assertEquals(11L, snapshot.get(fixture.player().getUniqueId()).remainingNanos() / 1_000_000_000L);
    }

    private static Fixture fixture(boolean bypass) {
        CombatTag feature = mock(CombatTag.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        UUID playerId = UUID.randomUUID();
        AtomicLong nanoTime = new AtomicLong(1_000_000_000L);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission(CombatTag.BYPASS_PERMISSION)).thenReturn(bypass);
        CombatTagService service = new CombatTagService(
                feature,
                settings(),
                nanoTime::get,
                Clock.fixed(Instant.parse("2026-08-05T15:00:00Z"), ZoneOffset.UTC)
        );
        return new Fixture(player, nanoTime, service);
    }

    private static CombatTagSettings settings() {
        return new CombatTagSettings(
                new CombatTagSettings.TaggingSettings(
                        CombatTagSettings.TagMode.BOTH,
                        15,
                        false,
                        new CombatTagSettings.WorldRule(CombatTagSettings.WorldMode.ALL, Set.of())
                ),
                new CombatTagSettings.AttributionSettings(
                        true,
                        new CombatTagSettings.ProjectileSettings(true, Set.of()),
                        true,
                        true,
                        Set.of()
                ),
                new CombatTagSettings.LifecycleSettings(true, true),
                new CombatTagSettings.TeleportSettings(true, true, Set.of(), false, false),
                new CombatTagSettings.LogoutSettings(false, false, false, List.of()),
                new CombatTagSettings.DisplaySettings(
                        false,
                        false,
                        new CombatTagSettings.ActionBarSettings(false, 5, 20, "█", "█")
                ),
                new CombatTagSettings.FeedbackSettings(0L)
        );
    }

    private static CombatOpponent opponent(String name, EntityType type) {
        return new CombatOpponent(UUID.randomUUID(), type, name, false);
    }

    private record Fixture(
            Player player,
            AtomicLong nanoTime,
            CombatTagService service
    ) {
    }
}
