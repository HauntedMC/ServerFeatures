package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdatePropertiesTest {

    @Test
    void builderDefaultsAreApplied() {
        UpdateProperties properties = new UpdateProperties.Builder().build();

        assertFalse(properties.isForced());
        assertFalse(properties.isOwnerOnly());
        assertEquals(0L, properties.getDelay());
        assertFalse(properties.getUpdateText());
    }

    @Test
    void builderAppliesExplicitValues() {
        UpdateProperties properties = new UpdateProperties.Builder()
                .forced(true)
                .ownerOnly(true)
                .delay(20L)
                .updateText(true)
                .build();

        assertTrue(properties.isForced());
        assertTrue(properties.isOwnerOnly());
        assertEquals(20L, properties.getDelay());
        assertTrue(properties.getUpdateText());
    }
}

