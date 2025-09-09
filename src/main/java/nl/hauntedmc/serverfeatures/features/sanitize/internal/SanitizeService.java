package nl.hauntedmc.serverfeatures.features.sanitize.internal;

import nl.hauntedmc.serverfeatures.features.sanitize.Sanitize;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.ServerRootResolver;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.VersionResolver;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SanitizeService {

    private final Sanitize feature;
    private final List<SanitizeTask> tasks = new ArrayList<>();

    public SanitizeService(Sanitize feature) {
        this.feature = feature;
    }

    public void addTask(SanitizeTask task) {
        if (task != null) tasks.add(task);
    }

    public void runStartupSanitize() {

        File serverRootFile = ServerRootResolver.resolve(feature.getPlugin(), feature.getLogger());
        final Path serverRoot = serverRootFile.toPath();

        String version = VersionResolver.resolveVersion(feature.getPlugin().getServer(), feature.getLogger());

        SanitizeContext ctx = new SanitizeContext(
                serverRoot,
                version
        );

        feature.getLogger().info("Starting startup folder cleanup (server version: " + version + ")");
        int changed = 0;
        int unchanged = 0;
        int errors = 0;

        for (SanitizeTask t : tasks) {
            String name = t.name();
            long t0 = System.nanoTime();
            try {
                SanitizeResult result = t.run(ctx);
                long dtMs = (System.nanoTime() - t0) / 1_000_000L;

                if (result.changed()) {
                    changed++;
                    feature.getLogger().info("[" + name + "] " + result.summary() + " (changed, " + dtMs + " ms)");
                } else {
                    unchanged++;
                    feature.getLogger().info("[" + name + "] " + result.summary() + " (unchanged, " + dtMs + " ms)");
                }
            } catch (Throwable ex) {
                errors++;
                feature.getLogger().warning("[" + name + "] Failed: " + ex.getMessage());
            }
        }

        feature.getLogger().info("Completed — tasks: " + tasks.size()
                + ", changed: " + changed + ", unchanged: " + unchanged + ", errors: " + errors);
    }
}
