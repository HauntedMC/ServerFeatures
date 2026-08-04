package nl.hauntedmc.serverfeatures.features.graveyard.model;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraveIdentifierTest {
    @Test
    void createsFriendlyPlayerTimeIdentifier() {
        String identifier = GraveIdentifier.create(
                "RemyMine",
                "Survival World",
                1_722_720_312_222L,
                ZoneId.of("Europe/Amsterdam")
        );

        assertEquals("RemyMine-23:25:12", identifier);
    }

    @Test
    void sanitizesAndBoundsOwnerName() {
        assertEquals(
                "player-00:00:00",
                GraveIdentifier.create(" !!! ", "world", 0L, ZoneId.of("UTC"))
        );
        assertEquals(
                "VeryLongPlayerNa-00:00:00",
                GraveIdentifier.create("VeryLongPlayerName!!!", "world", 0L, ZoneId.of("UTC"))
        );
    }
}
