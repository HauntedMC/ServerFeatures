package nl.hauntedmc.serverfeatures.features.votifier.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoteMessageTest {

    @Test
    void storesVoteMessageFields() {
        VoteMessage message = new VoteMessage("svc", "player", "127.0.0.1", 999L);

        assertEquals("votifier", message.getType());
        assertEquals("svc", message.getServiceName());
        assertEquals("player", message.getUsername());
        assertEquals("127.0.0.1", message.getAddress());
        assertEquals(999L, message.getVoteTimestamp());
    }

    @Test
    void privateNoArgConstructorKeepsDeserializerDefaults() throws Exception {
        Constructor<VoteMessage> ctor = VoteMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        VoteMessage message = ctor.newInstance();

        assertEquals("votifier", message.getType());
        assertNull(message.getServiceName());
        assertNull(message.getUsername());
        assertNull(message.getAddress());
        assertEquals(0L, message.getVoteTimestamp());
    }
}
