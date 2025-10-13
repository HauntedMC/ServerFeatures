package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.YamlSanitizeUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerPropertiesSanitizeTask implements SanitizeTask {

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    /**
     * Keys that must be enforced with the exact value below (order preserved for output).
     */
    private static final LinkedHashMap<String, String> ENFORCED = new LinkedHashMap<>();

    static {
        ENFORCED.put("accepts-transfers", "false");
        ENFORCED.put("allow-flight", "false");
        ENFORCED.put("broadcast-console-to-ops", "false");
        ENFORCED.put("broadcast-rcon-to-ops", "false");
        ENFORCED.put("bug-report-link", "https://hauntedmc.nl/support");
        ENFORCED.put("debug", "false");
        ENFORCED.put("enable-command-block", "false");
        ENFORCED.put("enable-jmx-monitoring", "false");
        ENFORCED.put("enable-query", "false");
        ENFORCED.put("enable-rcon", "false");
        ENFORCED.put("enable-status", "true");
        ENFORCED.put("enforce-secure-profile", "true");
        ENFORCED.put("enforce-whitelist", "false");
        ENFORCED.put("entity-broadcast-range-percentage", "100");
        ENFORCED.put("force-gamemode", "true");
        ENFORCED.put("function-permission-level", "2");
        ENFORCED.put("generate-structures", "true");
        ENFORCED.put("generator-settings", "{}");
        ENFORCED.put("hide-online-players", "false");
        ENFORCED.put("initial-disabled-packs", "");
        ENFORCED.put("initial-enabled-packs", "vanilla");
        ENFORCED.put("log-ips", "true");
        ENFORCED.put("max-chained-neighbor-updates", "1000000");
        ENFORCED.put("motd", "");
        ENFORCED.put("network-compression-threshold", "256");
        ENFORCED.put("online-mode", "false");
        ENFORCED.put("op-permission-level", "4");
        ENFORCED.put("pause-when-empty-seconds", "-1");
        ENFORCED.put("player-idle-timeout", "0");
        ENFORCED.put("prevent-proxy-connections", "false");
        ENFORCED.put("query.port", "25565");
        ENFORCED.put("rate-limit", "0");
        ENFORCED.put("rcon.password", "");
        ENFORCED.put("rcon.port", "25575");
        ENFORCED.put("region-file-compression", "deflate");
        ENFORCED.put("require-resource-pack", "false");
        ENFORCED.put("resource-pack", "");
        ENFORCED.put("resource-pack-id", "");
        ENFORCED.put("resource-pack-prompt", "");
        ENFORCED.put("resource-pack-sha1", "");
        ENFORCED.put("server-ip", "");
        ENFORCED.put("server-port", "25565");
        ENFORCED.put("sync-chunk-writes", "true");
        ENFORCED.put("text-filtering-config", "");
        ENFORCED.put("text-filtering-version", "0");
        ENFORCED.put("spawn-protection", "0");
        ENFORCED.put("use-native-transport", "true");
        ENFORCED.put("white-list", "false");
    }

    /**
     * Keys that should be grouped and preserved (values are NOT changed).
     */
    private static final List<String> PRESERVE_KEYS = List.of(
            "simulation-distance",
            "view-distance",
            "spawn-monsters",
            "allow-nether",
            "difficulty",
            "pvp",
            "gamemode",
            "hardcore",
            "level-name",
            "level-seed",
            "level-type",
            "max-players",
            "max-tick-time",
            "max-world-size"
    );

    @Override
    public String name() {
        return "ServerProperties";
    }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path file = ctx.serverRoot().resolve("server.properties").normalize();

        // Parse existing properties (order-preserving for "other" keys)
        LinkedHashMap<String, String> existing = readPropertiesLoose(file);

        // Build groups
        LinkedHashMap<String, String> groupPreserve = new LinkedHashMap<>();
        for (String k : PRESERVE_KEYS) {
            if (existing.containsKey(k)) {
                groupPreserve.put(k, existing.get(k));
            }
        }

        LinkedHashMap<String, String> groupOther = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : existing.entrySet()) {
            String k = e.getKey();
            if (!ENFORCED.containsKey(k) && !PRESERVE_KEYS.contains(k)) {
                groupOther.put(k, e.getValue());
            }
        }

        // Compose output: header, then "other/new-deprecated", then "preserved", enforced at the BOTTOM
        StringBuilder out = new StringBuilder();
        out.append("# Managed by HauntedMC Sanitize - DO NOT EDIT BELOW GROUPS UNLESS YOU KNOW WHAT YOU'RE DOING\n");
        out.append("# This file is rewritten on startup to enforce defaults and keep grouped layout.\n");
        out.append("\n");

        // 1) Other / new-deprecated
        out.append("## --- new/deprecated/other (preserved as-is) ---\n");
        if (groupOther.isEmpty()) {
            out.append("# (none)\n");
        } else {
            writeProps(out, groupOther);
        }
        out.append("\n");

        // 2) Gameplay/profile (preserved)
        out.append("## --- gameplay/profile (preserved if present) ---\n");
        if (groupPreserve.isEmpty()) {
            out.append("# (none present)\n");
        } else {
            // Write in canonical order of PRESERVE_KEYS (only the ones that exist)
            for (String k : PRESERVE_KEYS) {
                if (groupPreserve.containsKey(k)) {
                    writeProp(out, k, groupPreserve.get(k));
                }
            }
        }
        out.append("\n");

        // 3) Enforced defaults (BOTTOM)
        out.append("## --- HauntedMC enforced defaults (DO NOT CHANGE) ---\n");
        writeProps(out, ENFORCED);

        String newContent = ensureTrailingNewline(out.toString());

        // Write only if changed
        String current = Files.exists(file) ? Files.readString(file, UTF8) : "";
        if (!normalize(current).equals(normalize(newContent))) {
            Files.createDirectories(file.getParent() == null ? file.toAbsolutePath().getParent() : file.getParent());
            Files.writeString(file, newContent, UTF8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return SanitizeResult.changed("server.properties updated (groups and enforced defaults applied).");
        }

        return SanitizeResult.unchanged("server.properties already consistent.");
    }

    private static void writeProps(StringBuilder out, Map<String, String> props) {
        for (Map.Entry<String, String> e : props.entrySet()) {
            writeProp(out, e.getKey(), e.getValue());
        }
    }

    private static void writeProp(StringBuilder out, String key, String value) {
        // Write exactly "key=value". Do NOT trim value; we keep it as-is (including escapes like minecraft\:normal).
        out.append(key).append("=").append(value == null ? "" : value).append("\n");
    }

    /**
     * Loose parser for server.properties:
     * - Skips blank lines and comment lines starting with '#'
     * - Splits on the first '=' or ':' (vanilla supports both)
     * - Trims surrounding whitespace on key and value
     * - Later duplicates overwrite earlier ones (last wins)
     */
    private static LinkedHashMap<String, String> readPropertiesLoose(Path file) throws IOException {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(file)) return map;

        List<String> lines = Files.readAllLines(file, UTF8);
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int eq = indexOfFirst(line, '=', ':');
            if (eq < 0) continue;

            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            // keep value verbatim (no unescaping), to avoid changing user formatting
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static int indexOfFirst(String s, char a, char b) {
        int i1 = s.indexOf(a);
        int i2 = s.indexOf(b);
        if (i1 < 0) return i2;
        if (i2 < 0) return i1;
        return Math.min(i1, i2);
    }

    private static String normalize(String s) {
        return YamlSanitizeUtil.normalize(s);
    }

    private static String ensureTrailingNewline(String s) {
        return (s.endsWith("\n")) ? s : (s + "\n");
    }
}
