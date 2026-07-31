package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceholderHookTest {

    @Test
    void placeholderFailureFallsBackToPlainPlayerName() {
        Nametags feature = mock(Nametags.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        Player player = mock(Player.class);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLocalizationHandler()).thenThrow(new IllegalStateException("broken expansion"));
        when(player.getName()).thenReturn("Alice");

        PlaceholderHook hook = new PlaceholderHook(feature);
        try {
            assertEquals(Component.text("Alice"), hook.getNametagText(player));
            verify(logger).warning(contains("fallback"));
        } finally {
            hook.close();
        }
    }
}
