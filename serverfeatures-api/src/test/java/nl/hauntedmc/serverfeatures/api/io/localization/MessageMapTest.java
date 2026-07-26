package nl.hauntedmc.serverfeatures.api.io.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMapTest {

    @Test
    void addStoresEntriesAndMapIsLive() {
        MessageMap map = new MessageMap();
        map.add("a", "A");
        map.add("b", "B");

        assertEquals("A", map.getMessages().get("a"));
        assertEquals(2, map.getMessages().size());

        map.getMessages().put("c", "C");
        assertTrue(map.getMessages().containsKey("c"));
    }
}
