package nl.hauntedmc.serverfeatures.api.util.text.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagePlaceholdersTest {

    @Test
    void applyPlaceholdersUsesLongestKeyFirst() {
        MessagePlaceholders placeholders = MessagePlaceholders.builder()
                .addString("a", "ONE")
                .addString("ab", "TWO")
                .build();

        assertEquals("TWO/ONE", MessagePlaceholders.applyPlaceholders("{ab}/{a}", placeholders));
    }

    @Test
    void builderSupportsStringNumberComponentAndAddAll() {
        MessagePlaceholders base = MessagePlaceholders.builder()
                .addString("name", "Alex")
                .addNumber("count", 5)
                .build();

        MessagePlaceholders merged = MessagePlaceholders.builder()
                .addAll(base)
                .addComponent("colored", Component.text("X", NamedTextColor.RED))
                .build();

        String out = MessagePlaceholders.applyPlaceholders(
                "{name}:{count}:{colored}",
                merged
        );

        assertEquals("Alex:5:<red>X", out);
    }

    @Test
    void applyPlaceholdersReturnsOriginalForNullOrEmptyInput() {
        assertEquals(null, MessagePlaceholders.applyPlaceholders(null, MessagePlaceholders.empty()));
        assertEquals("x", MessagePlaceholders.applyPlaceholders("x", MessagePlaceholders.empty()));
        assertEquals("x", MessagePlaceholders.applyPlaceholders("x", null));
    }
}
