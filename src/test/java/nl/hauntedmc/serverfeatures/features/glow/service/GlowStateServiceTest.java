package nl.hauntedmc.serverfeatures.features.glow.service;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlowStateServiceTest {

    @Test
    void saveGlowStateSkipsPersistenceWhenPlayerRowIsMissing() {
        GlowStateService service = new GlowStateService(null, missingPlayerDirectory("44444444-4444-4444-4444-444444444444"));
        Player player = player("44444444-4444-4444-4444-444444444444", "Remy");
        GlowEffect effect = InterfaceProxy.of(GlowEffect.class, Map.of(
                "id", args -> "red"
        ));
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(persisted, merged);

        service.saveGlowState(session, player, Optional.of(effect));

        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void restoreGlowForSkipsWhenPlayerRowIsMissing() {
        GlowStateService service = new GlowStateService(null, missingPlayerDirectory("55555555-5555-5555-5555-555555555555"));
        Player player = player("55555555-5555-5555-5555-555555555555", "Remy");
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(persisted, merged);

        service.restoreGlowFor(session, player);

        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    private static PlayerDirectory missingPlayerDirectory(String uuid) {
        PlayerDirectory playerDirectory = mock(PlayerDirectory.class);
        UUID playerUuid = UUID.fromString(uuid);
        when(playerDirectory.findActiveIdentityCached(playerUuid)).thenReturn(Optional.empty());
        return playerDirectory;
    }

    private static Player player(String uuid, String name) {
        return InterfaceProxy.of(Player.class, Map.of(
                "getUniqueId", args -> UUID.fromString(uuid),
                "getName", args -> name
        ));
    }

    private static Session session(List<Object> persisted, List<Object> merged) {
        return InterfaceProxy.of(Session.class, Map.of(
                "persist", args -> {
                    persisted.add(args[0]);
                    return null;
                },
                "merge", args -> {
                    merged.add(args[0]);
                    return args[0];
                }
        ));
    }
}
