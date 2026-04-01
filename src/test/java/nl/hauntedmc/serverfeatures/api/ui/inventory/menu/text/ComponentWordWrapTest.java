package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentWordWrapTest {

    @Test
    void wrapReturnsEmptyForNullInput() {
        assertTrue(ComponentWordWrap.wrap(null, 20).isEmpty());
    }

    @Test
    void widthOneOrLessReturnsSingleNoItalicLine() {
        List<Component> lines = ComponentWordWrap.wrap(Component.text("hello"), 1);
        assertEquals(1, lines.size());
        assertEquals(TextDecoration.State.FALSE, lines.getFirst().style().decoration(TextDecoration.ITALIC));
    }

    @Test
    void wrapSplitsLongTextIntoMultipleLinesWithinWidth() {
        List<Component> lines = ComponentWordWrap.wrap(Component.text("one two three four five six"), 10);
        assertTrue(lines.size() >= 2);

        for (Component line : lines) {
            String plain = plainText(line);
            assertFalse(plain.isBlank());
            assertTrue(plain.length() <= 10);
            assertEquals(TextDecoration.State.FALSE, line.style().decoration(TextDecoration.ITALIC));
        }
    }

    private static String plainText(Component component) {
        StringBuilder out = new StringBuilder();
        FlattenerListener listener = new FlattenerListener() {
            @Override
            public void pushStyle(@NotNull Style style) {
            }

            @Override
            public void component(String text) {
                out.append(text);
            }

            @Override
            public void popStyle(@NotNull Style style) {
            }
        };
        ComponentFlattener.basic().flatten(component, listener);
        return out.toString();
    }
}
