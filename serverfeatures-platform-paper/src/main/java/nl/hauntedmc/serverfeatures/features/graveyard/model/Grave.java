package nl.hauntedmc.serverfeatures.features.graveyard.model;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;

import java.util.Objects;
import java.util.UUID;

public final class Grave {
    private final UUID graveId;
    private final String shortId;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String serverId;
    private final String inventoryScope;
    private final GraveLocation deathLocation;
    private volatile GraveLocation location;
    private volatile GravePlacementType placementType;
    private volatile GraveStatus status;
    private final long createdWallMillis;
    private final long createdActiveMillis;
    private volatile long expiresActiveMillis;
    private volatile Long pausedRemainingMillis;
    private volatile int itemEntryCount;
    private volatile int remainingExperience;
    private volatile long payloadRevision;
    private volatile String payloadChecksum;
    private final String deathCause;
    private final boolean ownerWasVanished;
    private volatile long visualGeneration;

    public Grave(
            UUID graveId,
            String shortId,
            UUID ownerUuid,
            String ownerName,
            String serverId,
            String inventoryScope,
            GraveLocation deathLocation,
            GraveLocation location,
            GravePlacementType placementType,
            GraveStatus status,
            long createdWallMillis,
            long createdActiveMillis,
            long expiresActiveMillis,
            Long pausedRemainingMillis,
            int itemEntryCount,
            int remainingExperience,
            long payloadRevision,
            String payloadChecksum,
            String deathCause,
            boolean ownerWasVanished
    ) {
        this.graveId = Objects.requireNonNull(graveId, "graveId");
        this.shortId = requireText(shortId, "shortId");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.ownerName = requireText(ownerName, "ownerName");
        this.serverId = requireText(serverId, "serverId");
        this.inventoryScope = requireText(inventoryScope, "inventoryScope");
        this.deathLocation = Objects.requireNonNull(deathLocation, "deathLocation");
        this.location = Objects.requireNonNull(location, "location");
        this.placementType = Objects.requireNonNull(placementType, "placementType");
        this.status = Objects.requireNonNull(status, "status");
        this.createdWallMillis = createdWallMillis;
        this.createdActiveMillis = createdActiveMillis;
        this.expiresActiveMillis = expiresActiveMillis;
        this.pausedRemainingMillis = pausedRemainingMillis;
        this.itemEntryCount = itemEntryCount;
        this.remainingExperience = remainingExperience;
        this.payloadRevision = payloadRevision;
        this.payloadChecksum = payloadChecksum;
        this.deathCause = deathCause;
        this.ownerWasVanished = ownerWasVanished;
    }

    public UUID graveId() {
        return graveId;
    }

    public String shortId() {
        return shortId;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public String serverId() {
        return serverId;
    }

    public String inventoryScope() {
        return inventoryScope;
    }

    public GraveLocation deathLocation() {
        return deathLocation;
    }

    public GraveLocation location() {
        return location;
    }

    public GravePlacementType placementType() {
        return placementType;
    }

    public GraveStatus status() {
        return status;
    }

    public long createdWallMillis() {
        return createdWallMillis;
    }

    public long createdActiveMillis() {
        return createdActiveMillis;
    }

    public long expiresActiveMillis() {
        return expiresActiveMillis;
    }

    public Long pausedRemainingMillis() {
        return pausedRemainingMillis;
    }

    public int itemEntryCount() {
        return itemEntryCount;
    }

    public int remainingExperience() {
        return remainingExperience;
    }

    public long payloadRevision() {
        return payloadRevision;
    }

    public String payloadChecksum() {
        return payloadChecksum;
    }

    public String deathCause() {
        return deathCause;
    }

    public boolean ownerWasVanished() {
        return ownerWasVanished;
    }

    public long visualGeneration() {
        return visualGeneration;
    }

    public synchronized long rotateVisualGeneration() {
        return ++visualGeneration;
    }

    public synchronized void relocate(GraveLocation next, GravePlacementType nextType) {
        location = Objects.requireNonNull(next, "next");
        placementType = Objects.requireNonNull(nextType, "nextType");
        rotateVisualGeneration();
    }

    public synchronized void updatePayload(GravePayload payload, String checksum, GraveStatus nextStatus) {
        itemEntryCount = payload.entries().size();
        remainingExperience = payload.remainingExperience();
        payloadRevision = payload.revision();
        payloadChecksum = checksum;
        status = Objects.requireNonNull(nextStatus, "nextStatus");
    }

    public synchronized void setStatus(GraveStatus nextStatus) {
        status = Objects.requireNonNull(nextStatus, "nextStatus");
    }

    public synchronized void pause(long remainingMillis, GraveStatus nextStatus) {
        pausedRemainingMillis = Math.max(0L, remainingMillis);
        status = Objects.requireNonNull(nextStatus, "nextStatus");
    }

    public synchronized void restore(long activeNow, long lifetimeMillis) {
        pausedRemainingMillis = null;
        expiresActiveMillis = Math.addExact(activeNow, lifetimeMillis);
        status = GraveStatus.ACTIVE;
    }

    public synchronized void resume(long activeNow) {
        long remaining = pausedRemainingMillis == null ? 0L : pausedRemainingMillis;
        pausedRemainingMillis = null;
        expiresActiveMillis = Math.addExact(activeNow, remaining);
        status = itemEntryCount == 0 && remainingExperience == 0
                ? GraveStatus.CLAIMED
                : GraveStatus.ACTIVE;
    }

    public long remainingActiveMillis(long activeNow) {
        Long paused = pausedRemainingMillis;
        return paused == null ? Math.max(0L, expiresActiveMillis - activeNow) : Math.max(0L, paused);
    }

    public GraveSnapshot snapshot(long activeNow) {
        GraveLocation current = location;
        return new GraveSnapshot(
                graveId,
                shortId,
                ownerUuid,
                ownerName,
                serverId,
                inventoryScope,
                current.worldUuid(),
                current.worldKey(),
                current.x(),
                current.y(),
                current.z(),
                status,
                placementType == GravePlacementType.REMOTE_ONLY,
                remainingActiveMillis(activeNow),
                itemEntryCount,
                remainingExperience
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
