package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageServiceTest {

    @Test
    void warmCachesTheProxyResolvedLanguageBeforeItsStageCompletes() {
        UUID uuid = UUID.randomUUID();
        PlayerData players = mock(PlayerData.class);
        when(players.findLanguage(uuid)).thenReturn(CompletableFuture.completedFuture(
                Optional.of(new PlayerLanguageSettings(1L, "AUTO", "NL"))
        ));

        LanguageService service = createService(players);

        assertEquals(Language.NL, service.warm(uuid).toCompletableFuture().join());
        assertEquals(Language.NL, service.get(uuid));
    }

    @Test
    void warmFallsBackWhenNoLanguageSettingsExist() {
        UUID uuid = UUID.randomUUID();
        PlayerData players = mock(PlayerData.class);
        when(players.findLanguage(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        LanguageService service = createService(players);

        assertEquals(Language.EN, service.warm(uuid).toCompletableFuture().join());
        assertEquals(Language.EN, service.get(uuid));
    }

    private static LanguageService createService(PlayerData players) {
        DataRegistryApi registry = mock(DataRegistryApi.class);
        when(registry.players()).thenReturn(players);
        return new LanguageService(mock(PlayerLanguage.class), registry);
    }
}
