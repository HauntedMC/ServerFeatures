package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.SafeFs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VersionsSanitizeTask implements SanitizeTask {

    @Override
    public String name() {
        return "Versions";
    }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path versionsDir = ctx.serverRoot().resolve("versions").normalize();

        if (!Files.isDirectory(versionsDir)) {
            return SanitizeResult.unchanged("Skipped — folder not found: " + versionsDir);
        }

        String keepFolder = ctx.minecraftVersion();
        List<Path> toRemove = new ArrayList<>();
        List<String> present = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(versionsDir)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String name = p.getFileName().toString();
                present.add(name);
                if (!name.equals(keepFolder)) {
                    toRemove.add(p);
                }
            }
        }

        if (toRemove.isEmpty()) {
            return SanitizeResult.unchanged("No changes. Present: " + String.join(", ", present));
        }

        int deleted = 0;
        List<String> failed = new ArrayList<>();
        for (Path p : toRemove) {
            if (SafeFs.deleteRecursively(p)) deleted++;
            else failed.add(p.getFileName().toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Keep: ").append(keepFolder).append("; Removed ").append(deleted)
                .append("/").append(toRemove.size()).append(" folder(s)");
        if (!present.isEmpty()) sb.append(" [present: ").append(String.join(", ", present)).append("]");
        if (!failed.isEmpty()) sb.append(" [failed: ").append(String.join(", ", failed)).append("]");

        boolean changed = deleted > 0 || !failed.isEmpty();
        return changed ? SanitizeResult.changed(sb.toString())
                : SanitizeResult.unchanged("No folders removed. " + sb);
    }
}
