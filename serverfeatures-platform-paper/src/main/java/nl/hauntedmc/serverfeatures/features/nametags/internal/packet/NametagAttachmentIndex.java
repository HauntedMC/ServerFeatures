package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe snapshot used by the PacketEvents lifecycle guard.
 *
 * <p>Packet callbacks may run away from the Bukkit main thread, so this index deliberately contains
 * only immutable ids and concurrent viewer sets. No Bukkit object is accessed from packet threads.</p>
 */
public final class NametagAttachmentIndex {
    private final ConcurrentMap<Integer, Attachment> attachments = new ConcurrentHashMap<>();

    public void register(int ownerEntityId, int nametagEntityId) {
        attachments.put(ownerEntityId, new Attachment(nametagEntityId));
    }

    public void unregister(int ownerEntityId, int nametagEntityId) {
        attachments.computeIfPresent(ownerEntityId, (ignored, current) ->
                current.nametagEntityId == nametagEntityId ? null : current
        );
    }

    public void markVisible(int ownerEntityId, UUID viewerId) {
        Attachment attachment = attachments.get(ownerEntityId);
        if (attachment != null) {
            attachment.visibleViewers.add(viewerId);
        }
    }

    public void markHidden(int ownerEntityId, UUID viewerId) {
        Attachment attachment = attachments.get(ownerEntityId);
        if (attachment != null) {
            attachment.visibleViewers.remove(viewerId);
        }
    }

    public boolean isVisible(int ownerEntityId, int nametagEntityId, UUID viewerId) {
        Attachment attachment = attachments.get(ownerEntityId);
        return attachment != null
                && attachment.nametagEntityId == nametagEntityId
                && attachment.visibleViewers.contains(viewerId);
    }

    public int[] appendNametagPassenger(UUID viewerId, int ownerEntityId, int[] passengers) {
        Attachment attachment = visibleAttachment(viewerId, ownerEntityId);
        if (attachment == null || contains(passengers, attachment.nametagEntityId)) {
            return passengers;
        }

        int[] updated = Arrays.copyOf(passengers, passengers.length + 1);
        updated[passengers.length] = attachment.nametagEntityId;
        return updated;
    }

    /**
     * Extends an owner-unload packet with all corresponding fake nametag ids visible to this viewer.
     * The returned array is the original instance when no modification is required.
     */
    public int[] appendDestroyedNametags(UUID viewerId, int[] entityIds) {
        LinkedHashSet<Integer> expanded = null;
        for (int entityId : entityIds) {
            Attachment attachment = visibleAttachment(viewerId, entityId);
            if (attachment == null) {
                continue;
            }

            attachment.visibleViewers.remove(viewerId);
            if (contains(entityIds, attachment.nametagEntityId)) {
                continue;
            }

            if (expanded == null) {
                expanded = new LinkedHashSet<>();
                for (int current : entityIds) {
                    expanded.add(current);
                }
            }
            expanded.add(attachment.nametagEntityId);
        }

        if (expanded == null) {
            return entityIds;
        }
        return expanded.stream().mapToInt(Integer::intValue).toArray();
    }

    public void clear() {
        attachments.clear();
    }

    private Attachment visibleAttachment(UUID viewerId, int ownerEntityId) {
        Attachment attachment = attachments.get(ownerEntityId);
        if (attachment == null || !attachment.visibleViewers.contains(viewerId)) {
            return null;
        }
        return attachment;
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static final class Attachment {
        private final int nametagEntityId;
        private final Set<UUID> visibleViewers = ConcurrentHashMap.newKeySet();

        private Attachment(int nametagEntityId) {
            this.nametagEntityId = nametagEntityId;
        }
    }
}
