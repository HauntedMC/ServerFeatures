package nl.hauntedmc.serverfeatures.features.glow.internal;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlowHandlerTest {

    @Test
    void indexesOnlyAnimatedEffectsAndTracksElapsedActivationTime() {
        Map<UUID, GlowHandler.TrackedEffect> active = new HashMap<>();
        Map<UUID, GlowHandler.TrackedEffect> animated = new HashMap<>();
        UUID playerId = UUID.fromString("4af8c2cf-72cd-4890-baf3-74e9b6194de3");
        GlowEffect animatedEffect = mock(GlowEffect.class);
        when(animatedEffect.isAnimated()).thenReturn(true);

        GlowHandler.TrackedEffect tracked = GlowHandler.trackEffect(
                active,
                animated,
                playerId,
                animatedEffect,
                TimeUnit.SECONDS.toNanos(100L)
        );

        assertSame(tracked, active.get(playerId));
        assertSame(tracked, animated.get(playerId));
        assertEquals(0L, tracked.elapsedSeconds(TimeUnit.SECONDS.toNanos(95L)));
        assertEquals(0L, tracked.elapsedSeconds(TimeUnit.SECONDS.toNanos(100L)));
        assertEquals(7L, tracked.elapsedSeconds(TimeUnit.SECONDS.toNanos(107L)));

        GlowEffect staticEffect = mock(GlowEffect.class);
        GlowHandler.TrackedEffect replacement = GlowHandler.trackEffect(
                active,
                animated,
                playerId,
                staticEffect,
                TimeUnit.SECONDS.toNanos(200L)
        );

        assertSame(replacement, active.get(playerId));
        assertSame(staticEffect, replacement.effect());
        assertFalse(animated.containsKey(playerId));
    }

    @Test
    void shutdownClearsRuntimeStateAndRemovesVisibleGlowWithoutPersistence() {
        UUID playerId = UUID.fromString("73a4c394-cdee-4612-a80e-47036a188596");
        Glow feature = mock(Glow.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        ServerFeatures plugin = mock(ServerFeatures.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class);
        GlowEffect effect = mock(GlowEffect.class);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        GlowHandler handler = new GlowHandler(feature);
        handler.trackEffect(playerId, effect, 100L);

        try (MockedStatic<ScoreboardManager> scoreboards = mockStatic(ScoreboardManager.class)) {
            handler.shutdown();
            scoreboards.verify(() -> ScoreboardManager.removeGlow(player));
        }

        assertFalse(handler.hasActiveGlow(player));
        verify(tasks).scheduleRepeatingTask(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
