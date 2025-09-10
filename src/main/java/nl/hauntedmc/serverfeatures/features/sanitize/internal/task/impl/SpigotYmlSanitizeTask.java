package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SpigotYmlSanitizeTask implements SanitizeTask {

    private static final Set<String> KNOWN_TOP = Set.of(
            "config-version",
            "settings",
            "messages",
            "commands",
            "advancements",
            "stats",
            "world-settings",
            "players"
    );

    private static final LinkedHashMap<String, Object> ENFORCED_MESSAGES = new LinkedHashMap<>();
    static {
        ENFORCED_MESSAGES.put("whitelist", "Op dit moment is deze Gamemode in onderhoud. Probeer het later nog eens.");
        ENFORCED_MESSAGES.put("unknown-command", "Dit commando wordt niet herkend.");
        ENFORCED_MESSAGES.put("server-full", "De server is vol. Met een Premium rank of hoger kun je alsnog joinen, zie store.hauntedmc.nl voor onze ranks.");
        ENFORCED_MESSAGES.put("outdated-client", "Gebruik versie {0} om te kunnen spelen.");
        ENFORCED_MESSAGES.put("outdated-server", "Gebruik versie {0} om te kunnen spelen.");
        ENFORCED_MESSAGES.put("restart", "De server gaat restarten. Je kunt over een ogenblik de server weer joinen.");
    }

    private static final LinkedHashMap<String, Object> ENFORCED_COMMANDS = new LinkedHashMap<>();
    static {
        ENFORCED_COMMANDS.put("tab-complete", 1);
        ENFORCED_COMMANDS.put("send-namespaced", false);
        ENFORCED_COMMANDS.put("log", true);
        ENFORCED_COMMANDS.put("spam-exclusions", Collections.emptyList());
        ENFORCED_COMMANDS.put("silent-commandblock-console", false);
        ENFORCED_COMMANDS.put("replace-commands", Collections.emptyList());
        ENFORCED_COMMANDS.put("enable-spam-exclusions", false);
    }

    private static final LinkedHashMap<String, Object> ENFORCED_SETTINGS = new LinkedHashMap<>();
    static {
        ENFORCED_SETTINGS.put("bungeecord", false);
        ENFORCED_SETTINGS.put("save-user-cache-on-stop-only", false);
        ENFORCED_SETTINGS.put("sample-count", 12);
        ENFORCED_SETTINGS.put("player-shuffle", 0);
        ENFORCED_SETTINGS.put("user-cache-size", 1000);
        ENFORCED_SETTINGS.put("moved-wrongly-threshold", 0.0625D);
        ENFORCED_SETTINGS.put("moved-too-quickly-multiplier", 10.0D);
        ENFORCED_SETTINGS.put("timeout-time", 60);
        ENFORCED_SETTINGS.put("restart-on-crash", true);
        ENFORCED_SETTINGS.put("restart-script", "./restart");
        ENFORCED_SETTINGS.put("netty-threads", 4);
        ENFORCED_SETTINGS.put("log-villager-deaths", true);
        ENFORCED_SETTINGS.put("log-named-deaths", true);
        ENFORCED_SETTINGS.put("debug", false);
    }

    @Override
    public String name() { return "SpigotYml"; }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path file = ctx.serverRoot().resolve("spigot.yml").normalize();

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        opts.setIndicatorIndent(1);
        opts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Yaml yaml = new Yaml(opts);

        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = yaml.load(raw);
            if (loaded instanceof Map<?, ?> m) root = toLinkedMap(m);
        }

        ensureSectionWithValues(root, "messages", ENFORCED_MESSAGES);
        ensureSectionWithValues(root, "commands", ENFORCED_COMMANDS);
        ensureSectionWithValues(root, "settings", ENFORCED_SETTINGS);

        Map<String, Object> settings = getOrCreateSection(root, "settings");
        Map<String, Object> attribute = getOrCreateSection(settings, "attribute");
        setIfDifferent(attribute, "maxAbsorption", mapOf("max", 2048.0D));
        setIfDifferent(attribute, "maxHealth", mapOf("max", 1024.0D));
        setIfDifferent(attribute, "movementSpeed", mapOf("max", 1024.0D));

        String dumped = yaml.dump(root).trim() + "\n";

        String header = """
# Managed by HauntedMC Sanitize
# Fixed sections: messages, commands, settings (incl. settings.attribute.*.max)
# Other sections (advancements, stats, world-settings, players, etc.) are preserved untouched.
""";

        StringBuilder out = new StringBuilder();
        out.append(header).append(dumped);

        // Unknown top-level keys (show only if not in KNOWN_TOP)
        List<String> unknown = new ArrayList<>();
        for (String k : root.keySet()) if (!KNOWN_TOP.contains(k)) unknown.add(k);
        if (!unknown.isEmpty()) {
            out.append("## --- new/deprecated/other (detected) ---\n");
            for (String k : unknown) out.append("# - ").append(k).append("\n");
        }

        String newContent = out.toString();
        String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";

        if (!normalize(current).equals(normalize(newContent))) {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            Files.writeString(file, newContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return SanitizeResult.changed("spigot.yml updated (fixed sections enforced).");
        }
        return SanitizeResult.unchanged("spigot.yml already compliant.");
    }

    private static void ensureSectionWithValues(Map<String, Object> root, String key, Map<String, Object> enforced) {
        Map<String, Object> sec = getOrCreateSection(root, key);
        for (Map.Entry<String, Object> e : enforced.entrySet()) {
            Object cur = sec.get(e.getKey());
            Object want = cloneForYaml(e.getValue());
            if (!Objects.equals(cur, want)) {
                sec.put(e.getKey(), want);
            }
        }
    }

    private static Map<String, Object> getOrCreateSection(Map<String, Object> parent, String key) {
        Object o = parent.get(key);
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> linked = toLinkedMap(m);
            if (o != linked) parent.put(key, linked);
            return linked;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private static void setIfDifferent(Map<String, Object> map, String key, Object want) {
        Object w = cloneForYaml(want);
        Object cur = map.get(key);
        if (!Objects.equals(cur, w)) {
            map.put(key, w);
        }
    }

    private static Map<String, Object> toLinkedMap(Map<?, ?> in) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : in.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) out.put(k, toLinkedMap(m));
            else if (v instanceof List<?> l) out.put(k, new ArrayList<>(l));
            else out.put(k, v);
        }
        return out;
    }

    private static Map<String, Object> mapOf(String k, Object v) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static Object cloneForYaml(Object v) {
        if (v instanceof Map<?, ?> m) return toLinkedMap(m);
        if (v instanceof List<?> l)  return new ArrayList<>(l);
        return v;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = s.replace("\r\n", "\n").replace("\r", "\n");
        return n.replaceAll("[\\s\\n\\r]+$", "");
    }
}
