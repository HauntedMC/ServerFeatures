package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Minimal read-only parser for the standard binary NBT format used by player .dat files.
 *
 * <p>The parser deliberately has no Bukkit or NMS dependency. Collections that the PlayerData
 * inspector never traverses (lists and primitive arrays) are consumed without retaining their
 * contents, which keeps diagnostic reads bounded.</p>
 */
final class BinaryNbtReader {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 1_000_000;
    private static final int BUFFER_SIZE = 8192;

    private BinaryNbtReader() {
    }

    static NbtCompound readGzip(byte[] compressed, int maxDecompressedBytes) throws IOException {
        Objects.requireNonNull(compressed, "compressed");
        if (maxDecompressedBytes < 1) {
            throw new IllegalArgumentException("maxDecompressedBytes must be positive");
        }

        byte[] decompressed = decompress(compressed, maxDecompressedBytes);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(decompressed))) {
            NbtType rootType = NbtType.fromId(input.readUnsignedByte());
            if (rootType != NbtType.COMPOUND) {
                throw new IOException("NBT root must be a compound");
            }
            readString(input);
            return readCompound(input, 0, new ParseBudget());
        }
    }

    private static byte[] decompress(byte[] compressed, int maxBytes) throws IOException {
        int initialCapacity = Math.min(Math.max(compressed.length * 2, BUFFER_SIZE), maxBytes);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int total = 0;
            int count;
            while ((count = gzip.read(buffer)) != -1) {
                if (count == 0) {
                    continue;
                }
                if (count > maxBytes - total) {
                    throw new IOException("Playerdata expands beyond the safe read limit");
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        }
    }

    private static NbtCompound readCompound(DataInputStream input, int depth, ParseBudget budget) throws IOException {
        requireDepth(depth);
        Map<String, NbtValue> values = new LinkedHashMap<>();
        while (true) {
            NbtType type = NbtType.fromId(input.readUnsignedByte());
            if (type == NbtType.END) {
                return new NbtCompound(values);
            }
            budget.consumeNode();
            String name = readString(input);
            values.put(name, readPayload(input, type, depth + 1, budget));
        }
    }

    private static NbtValue readPayload(
            DataInputStream input,
            NbtType type,
            int depth,
            ParseBudget budget
    ) throws IOException {
        requireDepth(depth);
        return switch (type) {
            case END -> throw new IOException("Unexpected TAG_End payload");
            case BYTE -> new NbtValue(type, input.readByte());
            case SHORT -> new NbtValue(type, input.readShort());
            case INT -> new NbtValue(type, input.readInt());
            case LONG -> new NbtValue(type, input.readLong());
            case FLOAT -> new NbtValue(type, input.readFloat());
            case DOUBLE -> new NbtValue(type, input.readDouble());
            case BYTE_ARRAY -> new NbtValue(type, readArray(input, 1));
            case STRING -> new NbtValue(type, readString(input));
            case LIST -> new NbtValue(type, readList(input, depth, budget));
            case COMPOUND -> new NbtValue(type, readCompound(input, depth, budget));
            case INT_ARRAY -> new NbtValue(type, readArray(input, Integer.BYTES));
            case LONG_ARRAY -> new NbtValue(type, readArray(input, Long.BYTES));
        };
    }

    private static ArrayInfo readArray(DataInputStream input, int bytesPerEntry) throws IOException {
        int length = readLength(input);
        skipFully(input, multiplyLength(length, bytesPerEntry));
        return new ArrayInfo(length);
    }

    private static ListInfo readList(DataInputStream input, int depth, ParseBudget budget) throws IOException {
        NbtType elementType = NbtType.fromId(input.readUnsignedByte());
        int length = readLength(input);
        if (elementType == NbtType.END && length != 0) {
            throw new IOException("Non-empty NBT list cannot use TAG_End elements");
        }
        budget.consumeNodes(length);
        skipListPayload(input, elementType, length, depth + 1, budget);
        return new ListInfo(elementType, length);
    }

    private static void skipListPayload(
            DataInputStream input,
            NbtType type,
            int length,
            int depth,
            ParseBudget budget
    ) throws IOException {
        requireDepth(depth);
        int fixedWidth = fixedWidth(type);
        if (fixedWidth > 0) {
            skipFully(input, multiplyLength(length, fixedWidth));
            return;
        }

        for (int index = 0; index < length; index++) {
            skipPayload(input, type, depth, budget);
        }
    }

    private static void skipPayload(
            DataInputStream input,
            NbtType type,
            int depth,
            ParseBudget budget
    ) throws IOException {
        requireDepth(depth);
        switch (type) {
            case END -> {
                // TAG_End has no payload and is only valid for an empty list.
            }
            case BYTE -> skipFully(input, Byte.BYTES);
            case SHORT -> skipFully(input, Short.BYTES);
            case INT, FLOAT -> skipFully(input, Integer.BYTES);
            case LONG, DOUBLE -> skipFully(input, Long.BYTES);
            case BYTE_ARRAY -> skipFully(input, multiplyLength(readLength(input), Byte.BYTES));
            case STRING -> skipString(input);
            case LIST -> {
                NbtType nestedType = NbtType.fromId(input.readUnsignedByte());
                int nestedLength = readLength(input);
                if (nestedType == NbtType.END && nestedLength != 0) {
                    throw new IOException("Non-empty NBT list cannot use TAG_End elements");
                }
                budget.consumeNodes(nestedLength);
                skipListPayload(input, nestedType, nestedLength, depth + 1, budget);
            }
            case COMPOUND -> skipCompound(input, depth + 1, budget);
            case INT_ARRAY -> skipFully(input, multiplyLength(readLength(input), Integer.BYTES));
            case LONG_ARRAY -> skipFully(input, multiplyLength(readLength(input), Long.BYTES));
        }
    }

    private static void skipCompound(DataInputStream input, int depth, ParseBudget budget) throws IOException {
        requireDepth(depth);
        while (true) {
            NbtType type = NbtType.fromId(input.readUnsignedByte());
            if (type == NbtType.END) {
                return;
            }
            budget.consumeNode();
            skipString(input);
            skipPayload(input, type, depth + 1, budget);
        }
    }

    private static int fixedWidth(NbtType type) {
        return switch (type) {
            case BYTE -> Byte.BYTES;
            case SHORT -> Short.BYTES;
            case INT, FLOAT -> Integer.BYTES;
            case LONG, DOUBLE -> Long.BYTES;
            default -> 0;
        };
    }

    private static int readLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("NBT collection has a negative length");
        }
        return length;
    }

    private static long multiplyLength(int length, int width) throws IOException {
        try {
            return Math.multiplyExact((long) length, width);
        } catch (ArithmeticException exception) {
            throw new IOException("NBT collection length is too large", exception);
        }
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Unexpected end of NBT string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void skipString(DataInputStream input) throws IOException {
        skipFully(input, input.readUnsignedShort());
    }

    private static void skipFully(DataInputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (input.read() < 0) {
                throw new EOFException("Unexpected end of NBT payload");
            }
            remaining--;
        }
    }

    private static void requireDepth(int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting exceeds the safe read limit");
        }
    }

    enum NbtType {
        END(0, "NBTTagEnd"),
        BYTE(1, "NBTTagByte"),
        SHORT(2, "NBTTagShort"),
        INT(3, "NBTTagInt"),
        LONG(4, "NBTTagLong"),
        FLOAT(5, "NBTTagFloat"),
        DOUBLE(6, "NBTTagDouble"),
        BYTE_ARRAY(7, "NBTTagByteArray"),
        STRING(8, "NBTTagString"),
        LIST(9, "NBTTagList"),
        COMPOUND(10, "NBTTagCompound"),
        INT_ARRAY(11, "NBTTagIntArray"),
        LONG_ARRAY(12, "NBTTagLongArray");

        private final int id;
        private final String displayName;

        NbtType(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        static NbtType fromId(int id) throws IOException {
            for (NbtType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new IOException("Unknown NBT tag type: " + id);
        }
    }

    record NbtValue(NbtType type, Object value) {
        NbtValue {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
        }
    }

    static final class NbtCompound {

        private final Map<String, NbtValue> values;

        private NbtCompound(Map<String, NbtValue> values) {
            this.values = Map.copyOf(values);
        }

        Set<String> keys() {
            return values.keySet();
        }

        NbtValue get(String key) {
            return values.get(key);
        }

        boolean has(String key, NbtType type) {
            NbtValue value = values.get(key);
            return value != null && value.type() == type;
        }

        NbtCompound compound(String key) {
            NbtValue value = values.get(key);
            return value != null && value.type() == NbtType.COMPOUND
                    ? (NbtCompound) value.value()
                    : null;
        }
    }

    record ArrayInfo(int length) {
    }

    record ListInfo(NbtType elementType, int length) {
    }

    private static final class ParseBudget {

        private int remainingNodes = MAX_NODES;

        void consumeNode() throws IOException {
            consumeNodes(1);
        }

        void consumeNodes(int count) throws IOException {
            if (count < 0 || count > remainingNodes) {
                throw new IOException("NBT node count exceeds the safe read limit");
            }
            remainingNodes -= count;
        }
    }
}
