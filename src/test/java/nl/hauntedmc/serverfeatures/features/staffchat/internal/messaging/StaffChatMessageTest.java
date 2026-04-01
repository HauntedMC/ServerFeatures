package nl.hauntedmc.serverfeatures.features.staffchat.internal.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StaffChatMessageTest {

    @Test
    void storesMessagePayloadAndType() {
        StaffChatMessage message = new StaffChatMessage("staff", "[S]", "hello", "Remy", "hub-1");

        assertEquals("staff", message.getType());
        assertEquals("[S]", message.getPrefix());
        assertEquals("hello", message.getMessage());
        assertEquals("Remy", message.getSenderName());
        assertEquals("hub-1", message.getSenderServer());
    }

    @Test
    void privateNoArgConstructorKeepsDeserializerDefaults() throws Exception {
        Constructor<StaffChatMessage> ctor = StaffChatMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        StaffChatMessage message = ctor.newInstance();

        assertEquals("staffchat", message.getType());
        assertNull(message.getPrefix());
        assertNull(message.getMessage());
        assertNull(message.getSenderName());
        assertNull(message.getSenderServer());
    }
}
