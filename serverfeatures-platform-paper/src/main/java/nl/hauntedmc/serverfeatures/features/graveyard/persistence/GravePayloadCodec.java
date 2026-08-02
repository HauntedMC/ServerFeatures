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
import java.util.HexFormat;
import java.util.List;
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
        if (payload.entries().size() > maximumEntries) {
            throw new IOException("Grave payload contains too many entries: " + payload.entries().size());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(CODEC_VERSION);
            output.writeLong(payload.revision());
            output.writeInt(payload.entries().size());
            for (GraveItemEntry entry : payload.entries()) {
                output.writeLong(entry.entryId().getMostSignificantBits());
                output.writeLong(entry.entryId().getLeastSignificantBits());
                output.writeInt(entry.preferredSlot());
                byte[] item = entry.serializedItem();
                validateItemSize(item.length);
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
        if (encoded.length > maximumPayloadBytes) {
            throw new IOException("Grave payload exceeds maximum size: " + encoded.length);
        }
        String actualChecksum = checksum(encoded);
        if (!MessageDigest.isEqual(actualChecksum.getBytes(), expectedChecksum.getBytes())) {
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
            for (int index = 0; index < count; index++) {
                UUID entryId = new UUID(input.readLong(), input.readLong());
                int preferredSlot = input.readInt();
                int itemLength = input.readInt();
                validateItemSize(itemLength);
                entries.add(new GraveItemEntry(entryId, preferredSlot, input.readNBytes(itemLength)));
                if (entries.getLast().serializedItem().length != itemLength) {
                    throw new IOException("Truncated Graveyard item payload");
                }
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

    private void validateItemSize(int size) throws IOException {
        if (size <= 0 || size > maximumItemBytes) {
            throw new IOException("Invalid grave item size " + size);
        }
    }
}
