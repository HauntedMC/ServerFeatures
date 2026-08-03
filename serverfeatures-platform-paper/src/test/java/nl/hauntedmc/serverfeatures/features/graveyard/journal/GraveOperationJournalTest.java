package nl.hauntedmc.serverfeatures.features.graveyard.journal;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveOperationJournalTest {
    @TempDir
    Path directory;

    @Test
    void captureTransitionsRoundTripAndDeleteAtomically() throws Exception {
        GraveOperationJournal journal = new GraveOperationJournal(directory, 1024 * 1024);
        UUID operation = UUID.randomUUID();
        UUID graveId = UUID.randomUUID();
        GraveLocation location = new GraveLocation(UUID.randomUUID(), "minecraft:world", 12.5, 64, -7.5, 90);
        Grave grave = new Grave(
                graveId, "ABC123", UUID.randomUUID(), "Player", "survival-1", "survival",
                location, location, GravePlacementType.DEATH_LOCATION, GraveStatus.ACTIVE,
                10L, 20L, 30L, null, 2, 7, 0L, "checksum", "minecraft:fall", false
        );
        EncodedGravePayload payload = new EncodedGravePayload(new byte[]{1, 2, 3, 4}, "encoded");

        journal.writeCapture(new CaptureJournalRecord(operation, CaptureJournalState.PREPARED, grave, payload));
        CaptureJournalRecord loaded = journal.loadCaptures().getFirst();
        assertEquals(CaptureJournalState.PREPARED, loaded.state());
        assertEquals(graveId, loaded.grave().graveId());
        assertArrayEquals(payload.bytes(), loaded.payload().bytes());

        journal.writeCapture(loaded.withState(CaptureJournalState.COMMITTED));
        assertEquals(CaptureJournalState.COMMITTED, journal.loadCaptures().getFirst().state());
        journal.deleteCapture(graveId);
        assertTrue(journal.loadCaptures().isEmpty());
    }

    @Test
    void malformedRecordsAreQuarantinedWithoutBreakingOtherRecords() throws Exception {
        GraveOperationJournal journal = new GraveOperationJournal(directory, 1024 * 1024);
        Path capture = directory.resolve("local/journal/capture");
        Files.writeString(capture.resolve("broken.properties"), "not-a-valid-record=true");

        assertTrue(journal.loadCaptures().isEmpty());
        try (var files = Files.list(directory.resolve("local/journal/corrupt"))) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void malformedNumericFieldsAreQuarantinedThroughTheJournalErrorPath() throws Exception {
        GraveOperationJournal journal = new GraveOperationJournal(directory, 1024 * 1024);
        UUID graveId = UUID.randomUUID();
        GraveLocation location = new GraveLocation(UUID.randomUUID(), "minecraft:world", 12.5, 64, -7.5, 90);
        Grave grave = new Grave(
                graveId, "ABC123", UUID.randomUUID(), "Player", "survival-1", "survival",
                location, location, GravePlacementType.DEATH_LOCATION, GraveStatus.ACTIVE,
                10L, 20L, 30L, null, 2, 7, 0L, "checksum", "minecraft:fall", false
        );
        journal.writeCapture(new CaptureJournalRecord(
                UUID.randomUUID(),
                CaptureJournalState.PREPARED,
                grave,
                new EncodedGravePayload(new byte[]{1, 2, 3}, "encoded")
        ));
        Path capturePath = directory.resolve("local/journal/capture").resolve(graveId + ".properties");
        Files.writeString(
                capturePath,
                Files.readString(capturePath).replace("itemEntryCount=2", "itemEntryCount=invalid")
        );

        UUID claimOperation = UUID.randomUUID();
        journal.writeClaim(new ClaimJournalRecord(
                claimOperation,
                ClaimJournalState.PREPARED,
                graveId,
                grave.ownerUuid(),
                grave.ownerUuid(),
                4L,
                2,
                15,
                new EncodedGravePayload(new byte[]{4, 5, 6}, "remaining")
        ));
        Path claimPath = directory.resolve("local/journal/claim").resolve(claimOperation + ".properties");
        Files.writeString(
                claimPath,
                Files.readString(claimPath).replace("previousRevision=4", "previousRevision=invalid")
        );

        assertTrue(journal.loadCaptures().isEmpty());
        assertTrue(journal.loadClaims().isEmpty());
        try (var files = Files.list(directory.resolve("local/journal/corrupt"))) {
            assertEquals(2L, files.count());
        }
    }

    @Test
    void malformedNumericFieldsAreQuarantined() throws Exception {
        GraveOperationJournal journal = new GraveOperationJournal(directory, 1024 * 1024);
        UUID operation = UUID.randomUUID();
        UUID graveId = UUID.randomUUID();
        GraveLocation location = new GraveLocation(UUID.randomUUID(), "minecraft:world", 1, 64, 1, 0);
        Grave grave = new Grave(
                graveId, "NUM123", UUID.randomUUID(), "Player", "survival-1", "survival",
                location, location, GravePlacementType.DEATH_LOCATION, GraveStatus.ACTIVE,
                10L, 20L, 30L, null, 1, 5, 0L, "checksum", null, false
        );
        journal.writeCapture(new CaptureJournalRecord(
                operation,
                CaptureJournalState.COMMITTED,
                grave,
                new EncodedGravePayload(new byte[]{1}, "encoded")
        ));
        Path capture = directory.resolve("local/journal/capture").resolve(graveId + ".properties");
        String invalid = Files.readString(capture).replace("createdWallMillis=10", "createdWallMillis=invalid");
        Files.writeString(capture, invalid);

        assertTrue(journal.loadCaptures().isEmpty());
        try (var files = Files.list(directory.resolve("local/journal/corrupt"))) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void claimTransitionsRoundTripAndQuarantinedTokensStayDiscoverable() throws Exception {
        GraveOperationJournal journal = new GraveOperationJournal(directory, 1024 * 1024);
        UUID operation = UUID.randomUUID();
        ClaimJournalRecord record = new ClaimJournalRecord(
                operation,
                ClaimJournalState.PREPARED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                4L,
                2,
                15,
                new EncodedGravePayload(new byte[]{5, 6, 7}, "remaining")
        );

        journal.writeClaim(record);
        ClaimJournalRecord loaded = journal.loadClaims().getFirst();
        assertEquals(operation, loaded.operationToken());
        assertEquals(ClaimJournalState.PREPARED, loaded.state());

        journal.writeClaim(loaded.withState(ClaimJournalState.PLAYER_APPLIED));
        assertEquals(ClaimJournalState.PLAYER_APPLIED, journal.loadClaims().getFirst().state());

        Path claim = directory.resolve("local/journal/claim");
        Files.writeString(claim.resolve(UUID.randomUUID() + ".properties"), "broken=true");
        journal.loadClaims();
        assertEquals(1, journal.quarantinedClaimTokens().size());

        journal.deleteClaim(operation);
        assertTrue(journal.loadClaims().isEmpty());
    }
}
