package nl.hauntedmc.serverfeatures.features.teleportation.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportActionTest {

    @Test
    void actionsExposeUniqueLowercaseConfigKeys() {
        assertEquals("randomtp", TeleportAction.RANDOM_TP.configKey());
        assertEquals("tppos", TeleportAction.TP_POS.configKey());

        List<String> keys = List.of(TeleportAction.values()).stream()
                .map(TeleportAction::configKey)
                .toList();
        Set<String> unique = keys.stream().collect(Collectors.toSet());

        assertEquals(keys.size(), unique.size());
        assertTrue(keys.stream().allMatch(k -> k.equals(k.toLowerCase())));
        assertTrue(keys.stream().allMatch(k -> !k.isBlank()));
    }
}
