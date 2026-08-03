package nl.hauntedmc.serverfeatures.features.graveyard.model;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveIdentifierTest {
    @Test
    void createsReadablePlayerWorldTimeIdentifier() {
        String identifier = GraveIdentifier.create(
                "RemyMine",
                "Survival World",
                1_722_720_312_222L,
                ZoneId.of("Europe/Amsterdam")
        );

        assertEquals("remymine-survival-world-20240803-232512-222", identifier);
    }

    @Test
    void sanitizesAndBoundsIdentifier() {
        String identifier = GraveIdentifier.create(
                " !!! ",
                "Very Long World Name With Spaces And Symbols !@#$%^&*() ".repeat(5),
                0L,
                ZoneId.of("UTC")
        );

        assertTrue(identifier.startsWith("player-very-long-world-name-with-spaces-and-symbols-"));
        assertTrue(identifier.endsWith("19700101-000000-000"));
        assertTrue(identifier.length() <= GraveIdentifier.MAXIMUM_LENGTH);
    }
}
