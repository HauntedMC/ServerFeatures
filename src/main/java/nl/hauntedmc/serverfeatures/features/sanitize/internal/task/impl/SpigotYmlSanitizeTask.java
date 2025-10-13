package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.YamlSanitizeUtil;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

        Yaml yaml = YamlSanitizeUtil.newYaml();

        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = yaml.load(raw);
            if (loaded instanceof Map<?, ?> m) root = YamlSanitizeUtil.toLinkedMap(m);
        }

        YamlSanitizeUtil.ensureSectionWithValues(root, "messages", ENFORCED_MESSAGES);
        YamlSanitizeUtil.ensureSectionWithValues(root, "commands", ENFORCED_COMMANDS);
        YamlSanitizeUtil.ensureSectionWithValues(root, "settings", ENFORCED_SETTINGS);

        Map<String, Object> settings = YamlSanitizeUtil.getOrCreateSection(root, "settings");
        Map<String, Object> attribute = YamlSanitizeUtil.getOrCreateSection(settings, "attribute");
        setIfDifferent(attribute, "maxAbsorption", YamlSanitizeUtil.mapOf("max", 2048.0D));
        setIfDifferent(attribute, "maxHealth", YamlSanitizeUtil.mapOf("max", 1024.0D));
        setIfDifferent(attribute, "movementSpeed", YamlSanitizeUtil.mapOf("max", 1024.0D));

        String dumped = yaml.dump(root).trim() + "\n"; // geen inline comments

        LinkedHashSet<String> controlled = buildControlledPaths();

        StringBuilder header = new StringBuilder();
        header.append("""
# Managed by HauntedMC Sanitize
# Fixed sections: messages, commands, settings (incl. settings.attribute.*.max)
# Other sections (advancements, stats, world-settings, players, etc.) are preserved untouched.
# NOTE: inline annotaties uitgeschakeld voor spigot.yml i.v.m. SnakeYAML/Bukkit comment-issue.
# Controlled paths:
""");
        for (String p : controlled) header.append("# - ").append(p).append("\n");
        header.append("\n");

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

        if (!YamlSanitizeUtil.normalize(current).equals(YamlSanitizeUtil.normalize(newContent))) {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            Files.writeString(file, newContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return SanitizeResult.changed("spigot.yml updated (fixed sections enforced).");
        }
        return SanitizeResult.unchanged("spigot.yml already compliant.");
    }

    private static @NotNull LinkedHashSet<String> buildControlledPaths() {
        LinkedHashSet<String> controlled = new LinkedHashSet<>();
        for (String k : ENFORCED_MESSAGES.keySet()) controlled.add("messages." + k);
        for (String k : ENFORCED_COMMANDS.keySet()) controlled.add("commands." + k);
        for (String k : ENFORCED_SETTINGS.keySet()) controlled.add("settings." + k);
        controlled.add("settings.attribute.maxAbsorption.max");
        controlled.add("settings.attribute.maxHealth.max");
        controlled.add("settings.attribute.movementSpeed.max");
        return controlled;
    }

    private static void setIfDifferent(Map<String, Object> map, String key, Object want) {
        Object w = YamlSanitizeUtil.cloneForYaml(want);
        Object cur = map.get(key);
        if (!Objects.equals(cur, w)) {
            map.put(key, w);
        }
    }
}
