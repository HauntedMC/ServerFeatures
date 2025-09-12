package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.YamlSanitizeUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class BukkitYmlSanitizeTask implements SanitizeTask {

    private static final Set<String> KNOWN_TOP = Set.of(
            "settings", "chunk-gc", "aliases", "spawn-limits", "ticks-per", "worlds"
    );

    private static final LinkedHashMap<String, Object> ENFORCED_SETTINGS = new LinkedHashMap<>();
    static {
        ENFORCED_SETTINGS.put("use-map-color-cache", true);
        ENFORCED_SETTINGS.put("warn-on-overload", true);
        ENFORCED_SETTINGS.put("permissions-file", "permissions.yml");
        ENFORCED_SETTINGS.put("update-folder", "update");
        ENFORCED_SETTINGS.put("plugin-profiling", false);
        ENFORCED_SETTINGS.put("connection-throttle", 4000);
        ENFORCED_SETTINGS.put("query-plugins", false);
        ENFORCED_SETTINGS.put("deprecated-verbose", "default");
        ENFORCED_SETTINGS.put("shutdown-message", "De server is uitgezet.");
        ENFORCED_SETTINGS.put("minimum-api", "none");
    }

    @Override
    public String name() { return "BukkitYml"; }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path file = ctx.serverRoot().resolve("bukkit.yml").normalize();

        Yaml yaml = YamlSanitizeUtil.newYaml();

        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = yaml.load(raw); // comments are ignored by SnakeYAML
            if (loaded instanceof Map<?, ?> m) {
                root = YamlSanitizeUtil.toLinkedMap(m);
            }
        }

        // settings
        Map<String, Object> settings = YamlSanitizeUtil.getOrCreateSection(root, "settings");
        for (Map.Entry<String, Object> e : ENFORCED_SETTINGS.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            Object cur = settings.get(k);
            if (!Objects.equals(cur, v)) {
                settings.put(k, v);
            }
        }

        // chunk-gc.period-in-ticks
        Map<String, Object> chunkGc = YamlSanitizeUtil.getOrCreateSection(root, "chunk-gc");
        Object cg = chunkGc.get("period-in-ticks");
        if (!Objects.equals(cg, 600)) {
            chunkGc.put("period-in-ticks", 600);
        }

        // aliases (force scalar)
        Object aliasesCur = root.get("aliases");
        if (!Objects.equals(aliasesCur, "now-in-commands.yml")) {
            root.put("aliases", "now-in-commands.yml");
        }

        // Prepare YAML dump
        String dumpedRaw = yaml.dump(root).trim() + "\n";

        // Build list of controlled (enforced) leaf paths for inline annotation
        LinkedHashSet<String> controlled = new LinkedHashSet<>();
        for (String k : ENFORCED_SETTINGS.keySet()) controlled.add("settings." + k);
        controlled.add("chunk-gc.period-in-ticks");
        controlled.add("aliases");

        String dumped = YamlSanitizeUtil.appendControlComments(dumpedRaw, controlled);

        String header = """
# Managed by HauntedMC Sanitize
# Fixed keys: settings.* (except allow-end), chunk-gc.period-in-ticks, aliases
# Free sections preserved: spawn-limits, ticks-per, worlds
# Note: Controlled entries are annotated inline with " # controlled by Sanitize".
""";

        StringBuilder out = new StringBuilder();
        out.append(header).append(dumped);

        List<String> unknown = new ArrayList<>();
        for (String k : root.keySet()) if (!KNOWN_TOP.contains(k)) unknown.add(k);
        if (!unknown.isEmpty()) {
            out.append("## --- new/deprecated/other (detected) ---\n");
            for (String k : unknown) out.append("# - ").append(k).append("\n");
        }

        String newContent = out.toString();

        String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        if (!YamlSanitizeUtil.normalize(current).equals(YamlSanitizeUtil.normalize(newContent))) {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            Files.writeString(file, newContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return SanitizeResult.changed("bukkit.yml updated (enforced keys applied).");
        }

        return SanitizeResult.unchanged("bukkit.yml already compliant.");
    }
}
