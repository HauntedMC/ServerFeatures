package nl.hauntedmc.serverfeatures.features.invtools.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvToolsServiceFactoryTest {

    @Test
    void matchingPositiveRuntimeVersionsAreAccepted() {
        assertDoesNotThrow(() -> InvToolsServiceFactory.requireMatchingDataVersion(4903, 4903));
    }

    @Test
    void mismatchedRuntimeVersionsFailClosed() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> InvToolsServiceFactory.requireMatchingDataVersion(4903, 4786)
        );

        assertTrue(exception.getMessage().contains("does not match"));
    }

    @Test
    void invalidPaperRuntimeVersionFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> InvToolsServiceFactory.requireMatchingDataVersion(0, 4903)
        );
    }

    @Test
    void invalidNbtApiRuntimeVersionFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> InvToolsServiceFactory.requireMatchingDataVersion(4903, 0)
        );
    }
}
