package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveItemEntry;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GravePayloadCodec {
    public static final int CODEC_VERSION = 1;
    private static final int MAGIC = 0x47525631;

    private final int maximumEntries;
    private final int maximumItemBytes;
    private final int maximumPayloadBytes;

    public GravePayloadCodec(int maximumEntries, int maximumItemBytes, int maximumPayloadBytes) {
        this.maximumEntries = maximumEntries;
        this.maximumItemBytes = maximumItemBytes;
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    public GraveItemEntry createEntry(UUID entryId, int preferredSlot, ItemStack item) throws IOException {
        if (entryId == null || item == null || item.getType().isAir() || item.getAmount() <= 0) {
            throw new IOException("Grave item entry is empty or incomplete");
        }
        validatePreferredSlot(preferredSlot);
        byte[] serialized = item.serializeAsBytes();
        validateItemSize(serialized.length);
        return new GraveItemEntry(entryId, preferredSlot, serialized);
    }

    public ItemStack decodeItem(GraveItemEntry entry) throws IOException {
        validateItemSize(entry.serializedItem().length);
        try {
            ItemStack item = ItemStack.deserializeBytes(entry.serializedItem());
            if (item.getType().isAir() || item.getAmount() <= 0) {
                throw new IOException("Decoded grave item is empty");
            }
            return item;
        } catch (RuntimeException exception) {
            throw new IOException("Could not decode grave item " + entry.entryId(), exception);
        }
    }

    public EncodedGravePayload encode(GravePayload payload) throws IOException {
        if (payload == null) {
            throw new IOException("Grave payload is missing");
        }
        if (payload.entries().size() > maximumEntries) {
            throw new IOException("Grave payload contains too many entries: " + payload.entries().size());
        }
        Set<UUID> entryIds = new HashSet<>(payload.entries().size());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(CODEC_VERSION);
            output.writeLong(payload.revision());
            output.writeInt(payload.entries().size());
            for (GraveItemEntry entry : payload.entries()) {
                if (entry == null || !entryIds.add(entry.entryId())) {
                    throw new IOException("Grave payload contains a missing or duplicate entry id");
                }
                validatePreferredSlot(entry.preferredSlot());
                output.writeLong(entry.entryId().getMostSignificantBits());
                output.writeLong(entry.entryId().getLeastSignificantBits());
                output.writeInt(entry.preferredSlot());
                byte[] item = entry.serializedItem();
                validateItemSize(item.length);
                decodeItem(entry);
                output.writeInt(item.length);
                output.write(item);
            }
            output.writeInt(payload.remainingExperience());
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > maximumPayloadBytes) {
            throw new IOException("Grave payload exceeds maximum size: " + encoded.length);
        }
        return new EncodedGravePayload(encoded, checksum(encoded));
    }

    public GravePayload decode(byte[] encoded, String expectedChecksum) throws IOException {
        if (encoded == null || encoded.length == 0) {
            throw new IOException("Grave payload is empty");
        }
        if (encoded.length > maximumPayloadBytes) {
            throw new IOException("Grave payload exceeds maximum size: " + encoded.length);
        }
        String actualChecksum = checksum(encoded);
        if (expectedChecksum == null || !MessageDigest.isEqual(
                actualChecksum.getBytes(StandardCharsets.US_ASCII),
                expectedChecksum.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IOException("Grave payload checksum mismatch");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Unknown Graveyard payload magic");
            }
            int version = input.readInt();
            if (version != CODEC_VERSION) {
                throw new IOException("Unsupported Graveyard payload codec " + version);
            }
            long revision = input.readLong();
            int count = input.readInt();
            if (count < 0 || count > maximumEntries) {
                throw new IOException("Invalid Graveyard payload entry count " + count);
            }
            List<GraveItemEntry> entries = new ArrayList<>(count);
            Set<UUID> entryIds = new HashSet<>(count);
            for (int index = 0; index < count; index++) {
                UUID entryId = new UUID(input.readLong(), input.readLong());
                if (!entryIds.add(entryId)) {
                    throw new IOException("Duplicate Graveyard payload entry id " + entryId);
                }
                int preferredSlot = input.readInt();
                validatePreferredSlot(preferredSlot);
                int itemLength = input.readInt();
                validateItemSize(itemLength);
                GraveItemEntry entry = new GraveItemEntry(entryId, preferredSlot, input.readNBytes(itemLength));
                if (entry.serializedItem().length != itemLength) {
                    throw new IOException("Truncated Graveyard item payload");
                }
                decodeItem(entry);
                entries.add(entry);
            }
            int experience = input.readInt();
            if (experience < 0 || input.available() != 0) {
                throw new IOException("Invalid trailing Graveyard payload data");
            }
            return new GravePayload(revision, entries, experience);
        } catch (RuntimeException exception) {
            throw new IOException("Could not decode Graveyard payload", exception);
        }
    }

    public String checksum(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static void validatePreferredSlot(int preferredSlot) throws IOException {
        if (preferredSlot < -1 || preferredSlot > 40) {
            throw new IOException("Invalid grave preferred slot " + preferredSlot);
        }
    }

    private void validateItemSize(int size) throws IOException {
        if (size <= 0 || size > maximumItemBytes) {
            throw new IOException("Invalid grave item size " + size);
        }
    }
}
