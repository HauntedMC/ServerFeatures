package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.SafeFs;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CacheSanitizeTask implements SanitizeTask {

    @Override
    public String name() { return "Cache"; }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path cacheDir = ctx.serverRoot().resolve("cache").normalize();

        if (!Files.isDirectory(cacheDir)) {
            return SanitizeResult.unchanged("Skipped — folder not found: " + cacheDir);
        }

        String expected = "mojang_" + ctx.minecraftVersion() + ".jar";
        List<Path> toRemove = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<String> others = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cacheDir, "*.jar")) {
            for (Path p : ds) {
                String fn = p.getFileName().toString();
                if (fn.equalsIgnoreCase(expected)) {
                    kept.add(fn);
                } else if (fn.startsWith("mojang_")) {
                    toRemove.add(p);
                    others.add(fn);
                } // non-mojang jars are left untouched on purpose
            }
        }

        if (toRemove.isEmpty()) {
            String keptStr = kept.isEmpty() ? "none present" : String.join(", ", kept);
            return SanitizeResult.unchanged("No changes. Keep: " + keptStr);
        }

        int deleted = 0;
        List<String> failed = new ArrayList<>();
        for (Path p : toRemove) {
            if (SafeFs.deleteRecursively(p)) deleted++;
            else failed.add(p.getFileName().toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Kept: ").append(kept.isEmpty() ? "none present" : String.join(", ", kept))
          .append("; Removed ").append(deleted).append("/").append(toRemove.size()).append(" jar(s)");
        if (!others.isEmpty()) sb.append(" [candidates: ").append(String.join(", ", others)).append("]");
        if (!failed.isEmpty()) sb.append(" [failed: ").append(String.join(", ", failed)).append("]");

        boolean changed = deleted > 0 || !failed.isEmpty();
        return changed ? SanitizeResult.changed(sb.toString())
                       : SanitizeResult.unchanged("No files removed. " + sb);
    }
}
