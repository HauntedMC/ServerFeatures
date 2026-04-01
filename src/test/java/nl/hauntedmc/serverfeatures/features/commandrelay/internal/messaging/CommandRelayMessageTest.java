package nl.hauntedmc.serverfeatures.features.commandrelay.internal.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandRelayMessageTest {

    @Test
    void constructorStoresFieldsAndEventType() {
        CommandRelayMessage message = new CommandRelayMessage("/restart now", "survival-1");

        assertEquals("commandrelay", message.getType());
        assertEquals("/restart now", message.getCommand());
        assertEquals("survival-1", message.getOriginServer());
    }

    @Test
    void privateNoArgConstructorKeepsDeserializerDefaults() throws Exception {
        Constructor<CommandRelayMessage> ctor = CommandRelayMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        CommandRelayMessage message = ctor.newInstance();

        assertEquals("commandrelay", message.getType());
        assertNull(message.getCommand());
        assertNull(message.getOriginServer());
    }
}
