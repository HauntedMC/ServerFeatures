package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import nl.hauntedmc.serverfeatures.features.playerdata.inspect.BinaryNbtReader.ArrayInfo;
import nl.hauntedmc.serverfeatures.features.playerdata.inspect.BinaryNbtReader.ListInfo;
import nl.hauntedmc.serverfeatures.features.playerdata.inspect.BinaryNbtReader.NbtCompound;
import nl.hauntedmc.serverfeatures.features.playerdata.inspect.BinaryNbtReader.NbtType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryNbtReaderTest {

    @Test
    void summarizesListsAndPrimitiveArraysWithoutRetainingContents() throws IOException {
        byte[] compressed = gzip(output -> {
            writeCompoundStart(output, "");

            writeHeader(output, 7, "bytes");
            output.writeInt(3);
            output.write(new byte[]{1, 2, 3});

            writeHeader(output, 11, "ints");
            output.writeInt(2);
            output.writeInt(10);
            output.writeInt(20);

            writeHeader(output, 12, "longs");
            output.writeInt(1);
            output.writeLong(30L);

            writeHeader(output, 9, "numbers");
            output.writeByte(3);
            output.writeInt(3);
            output.writeInt(1);
            output.writeInt(2);
            output.writeInt(3);

            writeCompoundStart(output, "nested");
            writeString(output, "value", "ok");
            writeEnd(output);
            writeEnd(output);
        });

        NbtCompound root = BinaryNbtReader.readGzip(compressed, 1024 * 1024);

        assertEquals(3, assertInstanceOf(ArrayInfo.class, root.get("bytes").value()).length());
        assertEquals(2, assertInstanceOf(ArrayInfo.class, root.get("ints").value()).length());
        assertEquals(1, assertInstanceOf(ArrayInfo.class, root.get("longs").value()).length());
        ListInfo list = assertInstanceOf(ListInfo.class, root.get("numbers").value());
        assertEquals(NbtType.INT, list.elementType());
        assertEquals(3, list.length());
        assertEquals("ok", root.compound("nested").get("value").value());
    }

    @Test
    void rejectsPayloadThatExpandsPastConfiguredLimit() throws IOException {
        byte[] compressed = gzip(output -> {
            writeCompoundStart(output, "");
            writeHeader(output, 7, "payload");
            output.writeInt(4096);
            output.write(new byte[4096]);
            writeEnd(output);
        });

        IOException exception = assertThrows(
                IOException.class,
                () -> BinaryNbtReader.readGzip(compressed, 256)
        );

        assertTrue(exception.getMessage().contains("safe read limit"));
    }

    @Test
    void rejectsNegativeCollectionLength() throws IOException {
        byte[] compressed = gzip(output -> {
            writeCompoundStart(output, "");
            writeHeader(output, 7, "payload");
            output.writeInt(-1);
            writeEnd(output);
        });

        IOException exception = assertThrows(
                IOException.class,
                () -> BinaryNbtReader.readGzip(compressed, 1024 * 1024)
        );

        assertTrue(exception.getMessage().contains("negative length"));
    }

    @Test
    void rejectsNonEmptyEndList() throws IOException {
        byte[] compressed = gzip(output -> {
            writeCompoundStart(output, "");
            writeHeader(output, 9, "invalid");
            output.writeByte(0);
            output.writeInt(1);
            writeEnd(output);
        });

        IOException exception = assertThrows(
                IOException.class,
                () -> BinaryNbtReader.readGzip(compressed, 1024 * 1024)
        );

        assertTrue(exception.getMessage().contains("TAG_End"));
    }

    @Test
    void rejectsExcessiveCompoundNesting() throws IOException {
        byte[] compressed = gzip(output -> {
            writeCompoundStart(output, "");
            for (int depth = 0; depth < 66; depth++) {
                writeCompoundStart(output, "level" + depth);
            }
            for (int depth = 0; depth < 67; depth++) {
                writeEnd(output);
            }
        });

        IOException exception = assertThrows(
                IOException.class,
                () -> BinaryNbtReader.readGzip(compressed, 1024 * 1024)
        );

        assertTrue(exception.getMessage().contains("nesting"));
    }

    private static byte[] gzip(NbtWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             DataOutputStream output = new DataOutputStream(gzip)) {
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    private static void writeCompoundStart(DataOutputStream output, String name) throws IOException {
        writeHeader(output, 10, name);
    }

    private static void writeString(DataOutputStream output, String name, String value) throws IOException {
        writeHeader(output, 8, name);
        output.writeUTF(value);
    }

    private static void writeHeader(DataOutputStream output, int type, String name) throws IOException {
        output.writeByte(type);
        output.writeUTF(name);
    }

    private static void writeEnd(DataOutputStream output) throws IOException {
        output.writeByte(0);
    }

    @FunctionalInterface
    private interface NbtWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
