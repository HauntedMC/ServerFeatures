package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravePacketRendererContractTest {
    @Test
    void packetSendBoundaryRetainsPacketWrapperType() {
        Method send = Arrays.stream(GravePacketRenderer.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("send"))
                .findFirst()
                .orElseThrow();

        assertTrue(Modifier.isStatic(send.getModifiers()));
        assertEquals(Player.class, send.getParameterTypes()[0]);
        assertEquals(PacketWrapper.class, send.getParameterTypes()[1]);
    }
}
