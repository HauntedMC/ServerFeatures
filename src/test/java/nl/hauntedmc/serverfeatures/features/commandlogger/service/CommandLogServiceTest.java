package nl.hauntedmc.serverfeatures.features.commandlogger.service;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandLogServiceTest {

    @Test
    void logServerCommandKeepsTheScalarPlayerIdNullWhenIdentityIsUnavailable() {
        String uuid = "11111111-1111-1111-1111-111111111111";
        PlayerDirectory directory = mock(PlayerDirectory.class);
        when(directory.findActiveIdentityCached(UUID.fromString(uuid))).thenReturn(Optional.empty());
        CommandLogService service = new CommandLogService(null, directory);
        Player player = InterfaceProxy.of(Player.class, Map.of("getUniqueId", args -> UUID.fromString(uuid), "getName", args -> "Remy"));
        List<Object> persisted = new ArrayList<>();
        Session session = InterfaceProxy.of(Session.class, Map.of("persist", args -> { persisted.add(args[0]); return null; }));

        service.logServerCommand(session, "survival", 123L, player, "spawn");

        CommandExecutionEntity entry = assertInstanceOf(CommandExecutionEntity.class, persisted.getFirst());
        assertNull(entry.getPlayerId());
        assertEquals("survival", entry.getServer());
    }
}
