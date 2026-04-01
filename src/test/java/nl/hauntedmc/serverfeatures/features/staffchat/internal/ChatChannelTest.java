package nl.hauntedmc.serverfeatures.features.staffchat.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatChannelTest {

    @Test
    void buildsPermissionFromChannelId() {
        ChatChannel channel = new ChatChannel("staff", "!");

        assertEquals("staff", channel.getId());
        assertEquals("!", channel.getPrefix());
        assertEquals("proxyfeatures.feature.staffchat.staff", channel.getPermission());
    }
}

