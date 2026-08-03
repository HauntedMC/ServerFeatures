package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveCommandVisibilityTest {
    @Test
    void completedGravesDisappearFromPlayerListsAndActiveSuggestions() {
        assertTrue(GraveManager.isOwnerListable(GraveStatus.ACTIVE));
        assertTrue(GraveManager.isOwnerListable(GraveStatus.PARTIAL));
        assertTrue(GraveManager.isOwnerListable(GraveStatus.ORPHANED_WORLD));
        assertTrue(GraveManager.isOwnerListable(GraveStatus.DELIVERY_PENDING));

        assertFalse(GraveManager.isOwnerListable(GraveStatus.CLAIMED));
        assertFalse(GraveManager.isOwnerListable(GraveStatus.EXPIRED));
        assertFalse(GraveManager.isOwnerListable(GraveStatus.CORRUPT));
        assertFalse(GraveManager.isOwnerListable(GraveStatus.ADMIN_RECOVERED));
        assertFalse(GraveManager.isOwnerListable(GraveStatus.PURGED));
    }

    @Test
    void administrativeSuggestionsMatchAllowedTransitions() {
        assertTrue(GraveManager.isRestorable(GraveStatus.EXPIRED));
        assertTrue(GraveManager.isRestorable(GraveStatus.ORPHANED_WORLD));
        assertFalse(GraveManager.isRestorable(GraveStatus.ACTIVE));

        assertTrue(GraveManager.isPurgeable(GraveStatus.EXPIRED));
        assertTrue(GraveManager.isPurgeable(GraveStatus.CORRUPT));
        assertTrue(GraveManager.isPurgeable(GraveStatus.ADMIN_RECOVERED));
        assertFalse(GraveManager.isPurgeable(GraveStatus.ACTIVE));
        assertFalse(GraveManager.isPurgeable(GraveStatus.PURGED));
    }
}
