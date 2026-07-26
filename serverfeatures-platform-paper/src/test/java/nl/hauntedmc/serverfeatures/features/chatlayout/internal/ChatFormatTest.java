package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatFormatTest {

    @Test
    void constructorAndMutatorsStoreValues() {
        ChatFormat format = new ChatFormat("vip", 5);
        format.setPrefix("[VIP]");
        format.setName("<name>");
        format.setSuffix(": ");
        format.setPrefixTooltip(List.of("prefix"));
        format.setNameTooltip(List.of("name"));
        format.setSuffixTooltip(List.of("suffix"));
        format.setPreClickCmd("/prefix");
        format.setNameClickCmd("/name");
        format.setSuffixClickCmd("/suffix");

        assertEquals("vip", format.getIdentifier());
        assertEquals(5, format.getIndex());
        assertEquals("[VIP]", format.getPrefix());
        assertEquals("<name>", format.getName());
        assertEquals(": ", format.getSuffix());
        assertEquals(List.of("prefix"), format.getPrefixTooltip());
        assertEquals(List.of("name"), format.getNameTooltip());
        assertEquals(List.of("suffix"), format.getSuffixTooltip());
        assertEquals("/prefix", format.getPreClickCmd());
        assertEquals("/name", format.getNameClickCmd());
        assertEquals("/suffix", format.getSuffixClickCmd());
    }
}

