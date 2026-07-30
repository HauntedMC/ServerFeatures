package nl.hauntedmc.serverfeatures.features.restart.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartMarkerStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsMarkerAndDeletesItAfterReady() throws Exception {
        RestartMarkerStore store = new RestartMarkerStore(
                temporaryDirectory.resolve("restart").resolve("autoreconnect.properties")
        );
        RestartMarker marker = new RestartMarker(
                "restart-id",
                "survival",
                100L,
                200L,
                5_000L,
                250L
        );

        store.save(marker);

        assertEquals(marker, store.load().orElseThrow());
        assertTrue(java.nio.file.Files.isRegularFile(store.path()));

        store.delete();

        assertFalse(java.nio.file.Files.exists(store.path()));
        assertTrue(store.load().isEmpty());
    }
}
