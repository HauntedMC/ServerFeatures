package nl.hauntedmc.serverfeatures.features.backup.internal.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a zip archive with relative paths starting at the given root.
 * Returns counters: [0] = files count, [1] = total bytes (uncompressed).
 */
public final class ZipUtil {

    private ZipUtil() {
    }

    public static long[] zipPaths(Path zipFile,
                                  Path rootBase,
                                  List<Path> inputs,
                                  int compressionLevel) throws IOException {

        if (compressionLevel < 0 || compressionLevel > 9) compressionLevel = Deflater.DEFAULT_COMPRESSION;

        Files.createDirectories(zipFile.getParent());

        long fileCount = 0L;
        long totalBytes = 0L;

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            zos.setLevel(compressionLevel);

            for (Path input : inputs) {
                Path normalized = input.normalize();
                if (Files.isDirectory(normalized)) {
                    try (var stream = Files.walk(normalized)) {
                        for (Path p : (Iterable<Path>) stream::iterator) {
                            if (Files.isDirectory(p)) continue;
                            String entryName = toEntryName(rootBase, p);
                            fileCount++;
                            totalBytes += Files.size(p);
                            putEntry(zos, p, entryName);
                        }
                    }
                } else if (Files.isRegularFile(normalized)) {
                    String entryName = toEntryName(rootBase, normalized);
                    fileCount++;
                    totalBytes += Files.size(normalized);
                    putEntry(zos, normalized, entryName);
                }
            }
        }

        return new long[]{fileCount, totalBytes};
    }

    private static void putEntry(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        try {
            entry.setTime(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException ignored) {
        }
        zos.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private static String toEntryName(Path rootBase, Path file) {
        Path rel = safeRelativize(rootBase, file);
        String s = rel.toString().replace('\\', '/');
        if (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static Path safeRelativize(Path rootBase, Path p) {
        try {
            return rootBase.relativize(p);
        } catch (IllegalArgumentException e) {
            return p.getFileName();
        }
    }
}
