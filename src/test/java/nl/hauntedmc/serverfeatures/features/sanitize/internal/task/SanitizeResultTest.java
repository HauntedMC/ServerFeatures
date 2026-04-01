package nl.hauntedmc.serverfeatures.features.sanitize.internal.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanitizeResultTest {

    @Test
    void changedFactoryMarksResultAsChanged() {
        SanitizeResult result = SanitizeResult.changed("updated");

        assertTrue(result.changed());
        assertEquals("updated", result.summary());
    }

    @Test
    void unchangedFactoryMarksResultAsUnchanged() {
        SanitizeResult result = SanitizeResult.unchanged("nothing");

        assertFalse(result.changed());
        assertEquals("nothing", result.summary());
    }
}

