package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges legacy configuration into a newer store without overwriting or reshaping target data.
 */
public final class ConfigMigrationMerger {

    private ConfigMigrationMerger() {
    }

    /**
     * Copies values that are absent from {@code target}, saving the target at most once.
     *
     * <p>An existing target branch always wins. In particular, an existing scalar or list is not
     * replaced when the legacy source contains a map at the same path.</p>
     *
     * @return the number of paths added to the target
     */
    public static int mergeMissing(ConfigView target, Object source) {
        Objects.requireNonNull(target, "target");
        ConfigNode sourceNode = ConfigNode.ofRaw(source, "<migration-source>");
        if (sourceNode.isNull()) {
            return 0;
        }

        Map<String, Object> additions = new LinkedHashMap<>();
        collectMissing(sourceNode, target.node(), "", additions);
        if (!additions.isEmpty()) {
            target.batch(batch -> additions.forEach(batch::put));
        }
        return additions.size();
    }

    /**
     * Merges entries whose keys are configuration paths. Map values are recursively expanded so
     * partially configured target sections still receive missing nested defaults.
     *
     * @return the paths added to the target
     */
    public static Set<String> mergeMissingPaths(ConfigView target, Map<String, ?> sourcePaths) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourcePaths, "sourcePaths");
        ConfigNode targetRoot = target.node();
        Map<String, Object> additions = new LinkedHashMap<>();
        sourcePaths.forEach((path, value) -> {
            if (path == null || path.isBlank() || value == null) {
                return;
            }
            collectMissing(
                    ConfigNode.ofRaw(value, "<migration-source>." + path),
                    targetRoot.getAt(path),
                    path,
                    additions
            );
        });
        if (!additions.isEmpty()) {
            target.batch(batch -> additions.forEach(batch::put));
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(additions.keySet()));
    }

    private static void collectMissing(
            ConfigNode source,
            ConfigNode target,
            String path,
            Map<String, Object> additions
    ) {
        Map<String, ConfigNode> sourceChildren = source.children();
        if (!sourceChildren.isEmpty()) {
            if (!target.isNull() && !(target.raw() instanceof Map<?, ?>)) {
                return;
            }
            for (Map.Entry<String, ConfigNode> entry : sourceChildren.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                collectMissing(entry.getValue(), target.get(entry.getKey()), childPath, additions);
            }
            return;
        }

        if (target.isNull() && !path.isEmpty()) {
            additions.put(path, source.raw());
        }
    }
}
