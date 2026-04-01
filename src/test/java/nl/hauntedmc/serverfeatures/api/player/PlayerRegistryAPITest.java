package nl.hauntedmc.serverfeatures.api.player;

import nl.hauntedmc.serverfeatures.api.APIRegistry;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRegistryAPITest {

    @AfterEach
    void cleanup() {
        APIRegistry.clear();
    }

    @Test
    void fallsBackToNlWhenLanguageApiNotRegistered() {
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getUniqueId", args -> UUID.randomUUID()
        ));
        assertEquals(Language.NL, PlayerRegistryAPI.getPlayerLanguage(player));
    }

    @Test
    void usesRegisteredLanguageApiWhenAvailable() {
        UUID uuid = UUID.randomUUID();
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getUniqueId", args -> uuid
        ));

        LanguageAPI languageAPI = new LanguageAPI() {
            @Override
            public Language get(UUID playerUuid) {
                return Language.DE;
            }

            @Override
            public void set(UUID playerUuid, Language language) {
            }
        };

        APIRegistry.register(LanguageAPI.class, languageAPI);

        assertEquals(Language.DE, PlayerRegistryAPI.getPlayerLanguage(player));
    }
}
