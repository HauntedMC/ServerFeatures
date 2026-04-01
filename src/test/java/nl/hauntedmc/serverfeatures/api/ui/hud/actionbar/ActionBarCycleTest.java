package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBarCycleTest {

    @Test
    void builderCollectsEntriesAndClampsGapSeconds() {
        ActionBarEntry entry = ActionBarEntry.of(Component.text("x"), 2);
        ActionBarCycle cycle = ActionBarCycle.builder()
                .add(entry)
                .gapSeconds(-5)
                .build();

        assertEquals(1, cycle.entries().size());
        assertEquals(0, cycle.gapSeconds());
        assertThrows(UnsupportedOperationException.class, () -> cycle.entries().add(entry));
    }

    @Test
    void emptyBuilderProducesEmptyEntryList() {
        ActionBarCycle cycle = ActionBarCycle.builder().build();
        assertTrue(cycle.entries().isEmpty());
    }
}
