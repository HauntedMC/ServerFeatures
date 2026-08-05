package nl.hauntedmc.serverfeatures.features.combattag.service;

import nl.hauntedmc.serverfeatures.api.combat.CombatOpponent;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.features.combattag.CombatTag;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        verify(fixture.feature()).publishAppliedTag(fixture.player(), CombatTagResult.TAGGED);
        verify(fixture.feature()).publishAppliedTag(fixture.player(), CombatTagResult.RETAGGED);
    }

    @Test
    void publicApiTagsPublishTheSameAppliedTransition() {
        Fixture fixture = fixture(false);
        Entity opponent = mock(Entity.class);
        UUID opponentId = UUID.randomUUID();
        when(opponent.getUniqueId()).thenReturn(opponentId);
        when(opponent.getType()).thenReturn(EntityType.ZOMBIE);
        when(opponent.getName()).thenReturn("Zombie");

        assertEquals(
                CombatTagResult.TAGGED,
                fixture.service().tag(fixture.player(), opponent, CombatTagReason.EXTERNAL)
        );

        verify(fixture.feature()).publishAppliedTag(fixture.player(), CombatTagResult.TAGGED);
    }

    @Test
    void outgoingOnlyTagDoesNotInventALogoutAttacker() {
        Fixture fixture = fixture(false);
        CombatOpponent target = opponent("target", EntityType.ZOMBIE);

        fixture.service().tagOutgoing(
                fixture.player(),
                target,
                target.uniqueId(),
                CombatTagReason.MELEE
        );

        var stored = fixture.service().snapshotForReload().get(fixture.player().getUniqueId());
        assertNull(stored.logoutOpponent());
        assertNull(stored.logoutDamageSourceId());
        assertNull(stored.logoutReason());
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
                CombatTagReason.PROJECTILE
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
        assertEquals(CombatTagReason.PROJECTILE, stored.logoutReason());
    }

    @Test
    void displayedOpponentDeathFallsBackToTheRetainedIncomingAttacker() {
        Fixture fixture = fixture(false);
        CombatOpponent attacker = opponent("attacker", EntityType.ZOMBIE);
        CombatOpponent attacked = opponent("attacked", EntityType.SKELETON);

        fixture.service().tagIncoming(
                fixture.player(),
                attacker,
                attacker.uniqueId(),
                CombatTagReason.PROJECTILE
        );
        fixture.service().tagOutgoing(
                fixture.player(),
                attacked,
                attacked.uniqueId(),
                CombatTagReason.MELEE
        );

        fixture.service().handleOpponentDeath(entity(attacked));

        var visible = fixture.service().getTag(fixture.player()).orElseThrow();
        var stored = fixture.service().snapshotForReload().get(fixture.player().getUniqueId());
        assertEquals(attacker, visible.opponent());
        assertEquals(CombatTagReason.PROJECTILE, visible.reason());
        assertEquals(attacker, stored.logoutOpponent());
        assertEquals(CombatTagReason.PROJECTILE, stored.logoutReason());
    }

    @Test
    void retainedIncomingAttackerDeathKeepsTheOutgoingTagWithoutStaleAttribution() {
        Fixture fixture = fixture(false);
        CombatOpponent attacker = opponent("attacker", EntityType.ZOMBIE);
        CombatOpponent attacked = opponent("attacked", EntityType.SKELETON);

        fixture.service().tagIncoming(
                fixture.player(),
                attacker,
                attacker.uniqueId(),
                CombatTagReason.PROJECTILE
        );
        fixture.service().tagOutgoing(
                fixture.player(),
                attacked,
                attacked.uniqueId(),
                CombatTagReason.MELEE
        );

        fixture.service().handleOpponentDeath(entity(attacker));

        var visible = fixture.service().getTag(fixture.player()).orElseThrow();
        var stored = fixture.service().snapshotForReload().get(fixture.player().getUniqueId());
        assertEquals(attacked, visible.opponent());
        assertEquals(CombatTagReason.MELEE, visible.reason());
        assertNull(stored.logoutOpponent());
        assertNull(stored.logoutDamageSourceId());
        assertNull(stored.logoutReason());
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
        verify(fixture.feature(), never()).publishAppliedTag(
                fixture.player(),
                CombatTagResult.BYPASSED
        );
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

    @Test
    void commandPlaceholderReplacementIsSinglePassAndSanitizesIsoControls() {
        String result = CombatTagService.replaceCommandPlaceholders(
                "say {attacker} {player} {unknown}",
                Map.of(
                        "attacker", "mob\n\t\u0085{player}",
                        "player", "Alice"
                )
        );

        assertEquals("say mob   {player} Alice {unknown}", result);
    }

    @Test
    void serverShutdownNeverPunishesTaggedPlayers() {
        CombatTag feature = mock(CombatTag.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission(CombatTag.BYPASS_PERMISSION)).thenReturn(false);
        CombatTagService service = new CombatTagService(
                feature,
                settings(true),
                System::nanoTime,
                Clock.systemUTC(),
                () -> true,
                () -> true
        );
        CombatOpponent opponent = opponent("attacker", EntityType.ZOMBIE);
        service.tagIncoming(player, opponent, opponent.uniqueId(), CombatTagReason.MELEE);

        service.handleQuit(player, false);

        assertFalse(service.isTagged(player));
        verify(feature, never()).broadcastMessage(anyString(), anyMap());
    }

    @Test
    void publicWritesFailFastAwayFromTheServerThread() {
        CombatTag feature = mock(CombatTag.class);
        Player player = mock(Player.class);
        CombatTagService service = new CombatTagService(
                feature,
                settings(),
                System::nanoTime,
                Clock.systemUTC(),
                () -> false
        );
        CombatOpponent opponent = opponent("zombie", EntityType.ZOMBIE);

        assertThrows(
                IllegalStateException.class,
                () -> service.tagIncoming(
                        player,
                        opponent,
                        opponent.uniqueId(),
                        CombatTagReason.MELEE
                )
        );
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
        return new Fixture(feature, player, nanoTime, service);
    }

    private static CombatTagSettings settings() {
        return settings(false);
    }

    private static CombatTagSettings settings(boolean broadcastLogout) {
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
                new CombatTagSettings.LogoutSettings(
                        broadcastLogout,
                        false,
                        broadcastLogout,
                        false,
                        List.of()
                ),
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

    private static Entity entity(CombatOpponent opponent) {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(opponent.uniqueId());
        return entity;
    }

    private record Fixture(
            CombatTag feature,
            Player player,
            AtomicLong nanoTime,
            CombatTagService service
    ) {
    }
}
