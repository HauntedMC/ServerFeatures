package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBarEntryTest {

    @Test
    void staticEntryStoresComponentAndClampsSeconds() {
        ActionBarEntry entry = ActionBarEntry.of(Component.text("msg"), -1);
        assertFalse(entry.isPerPlayer());
        assertEquals(0, entry.seconds());
        assertEquals(Component.text("msg"), entry.component());
    }

    @Test
    void perPlayerEntryStoresSupplier() {
        ActionBarEntry entry = ActionBarEntry.perPlayer(player -> Component.text("hello"), 3);
        assertTrue(entry.isPerPlayer());
        assertEquals(3, entry.seconds());
        assertNotNull(entry.perPlayer());
    }
}
