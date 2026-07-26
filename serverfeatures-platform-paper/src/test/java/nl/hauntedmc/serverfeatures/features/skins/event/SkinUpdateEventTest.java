package nl.hauntedmc.serverfeatures.features.skins.event;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SkinUpdateEventTest {

    @Test
    void eventExposesPayloadAndHandlerList() {
        Player player = InterfaceProxy.of(Player.class, Map.of());
        SkinUpdateEvent event = new SkinUpdateEvent(player, SkinUpdateType.SET, "Notch");

        assertSame(player, event.getPlayer());
        assertEquals(SkinUpdateType.SET, event.getType());
        assertEquals("Notch", event.getNewSkinName());
        assertSame(SkinUpdateEvent.getHandlerList(), event.getHandlers());
    }
}

