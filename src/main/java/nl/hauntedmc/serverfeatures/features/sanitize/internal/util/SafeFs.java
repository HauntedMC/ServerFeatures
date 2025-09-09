package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class SafeFs {

    private SafeFs() {}

    /**
     * Recursively deletes a file or directory tree. Returns true if fully deleted, false otherwise.
     * Fails closed: if any error occurs, returns false.
     */
    public static boolean deleteRecursively(Path target) {
        if (target == null) return false;

        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return true; // already gone
            }

            // Normalize to avoid surprises
            final Path normalized = target.normalize();

            Files.walkFileTree(normalized, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
