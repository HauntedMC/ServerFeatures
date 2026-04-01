package nl.hauntedmc.serverfeatures.features.staffchat.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatChannelHandlerTest {

    @Test
    void resolvesConfiguredPrefixesToChannels() {
        ChatChannelHandler handler = new ChatChannelHandler("staff", "!", "team", "#", "admin", "$");

        assertEquals("staff", handler.getChannelByPrefix("!").getId());
        assertEquals("team", handler.getChannelByPrefix("#").getId());
        assertEquals("admin", handler.getChannelByPrefix("$").getId());
        assertNull(handler.getChannelByPrefix("?"));
    }

    @Test
    void ignoresBlankOrNullChannelEntries() {
        ChatChannelHandler handler = new ChatChannelHandler("staff", " ", "team", null, "admin", "$");

        assertNull(handler.getChannelByPrefix(" "));
        assertNull(handler.getChannelByPrefix(null));
        assertEquals("admin", handler.getChannelByPrefix("$").getId());
    }
}

