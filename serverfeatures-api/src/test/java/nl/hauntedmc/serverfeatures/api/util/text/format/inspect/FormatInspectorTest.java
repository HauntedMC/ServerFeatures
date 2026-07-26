package nl.hauntedmc.serverfeatures.api.util.text.format.inspect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatInspectorTest {

    @Test
    void detectsFormattingInStrings() {
        assertTrue(FormatInspector.containsAnyFormatting("&aHello"));
        assertTrue(FormatInspector.containsAnyFormatting("<green>Hello</green>"));
        assertFalse(FormatInspector.containsAnyFormatting("plain text"));

        assertTrue(FormatInspector.containsFormatting(
                "<#aabbcc>x",
                EnumSet.of(TextFormatter.InputFormat.HEX_MINI)
        ));
        assertFalse(FormatInspector.containsFormatting(
                "<#aabbcc>x",
                EnumSet.of(TextFormatter.InputFormat.LEGACY_AMPERSAND)
        ));
    }

    @Test
    void detectsFormattingInComponents() {
        Component formatted = Component.text("x", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/x"));

        assertTrue(FormatInspector.hasAnyFormatting(formatted));
        assertTrue(FormatInspector.hasFormatting(formatted, EnumSet.of(FormatInspector.ComponentAspect.COLOR)));
        assertTrue(FormatInspector.hasFormatting(formatted, EnumSet.of(FormatInspector.ComponentAspect.DECORATION)));
        assertTrue(FormatInspector.hasFormatting(formatted, EnumSet.of(FormatInspector.ComponentAspect.EVENTS)));
        assertFalse(FormatInspector.hasFormatting(Component.text("plain"), EnumSet.of(FormatInspector.ComponentAspect.COLOR)));
    }

    @Test
    void hasFormattingRejectsNullComponent() {
        assertThrows(NullPointerException.class, () -> FormatInspector.hasFormatting(null, EnumSet.allOf(FormatInspector.ComponentAspect.class)));
    }
}
