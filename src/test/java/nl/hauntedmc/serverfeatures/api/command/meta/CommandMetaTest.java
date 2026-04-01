package nl.hauntedmc.serverfeatures.api.command.meta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandMetaTest {

    @Test
    void builderCreatesRecordWithProvidedValues() {
        CommandMeta meta = new CommandMeta.Builder("feature")
                .description("desc")
                .usage("/feature <arg>")
                .aliases(List.of("f", "ft"))
                .permission("feature.use")
                .build();

        assertEquals("feature", meta.name());
        assertEquals("desc", meta.description());
        assertEquals("/feature <arg>", meta.usage());
        assertEquals(List.of("f", "ft"), meta.aliases());
        assertEquals("feature.use", meta.permission());
    }

    @Test
    void builderRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new CommandMeta.Builder(" "));
    }

    @Test
    void usageAndAliasesCanBeNull() {
        CommandMeta meta = new CommandMeta.Builder("cmd").usage(null).aliases(null).build();
        assertNull(meta.usage());
        assertNull(meta.aliases());
    }
}
