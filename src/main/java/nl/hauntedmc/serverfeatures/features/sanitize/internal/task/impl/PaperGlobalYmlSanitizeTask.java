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

public class PaperGlobalYmlSanitizeTask implements SanitizeTask {

    private static final Set<String> KNOWN_TOP = Set.of(
            "collisions","messages","unsupported-settings","watchdog","scoreboards","proxies",
            "spam-limiter","spark","player-auto-save","misc","commands","console","item-validation",
            "logging","anticheat","block-updates","chunk-loading-advanced","chunk-loading-basic","chunk-system"
    );

    @Override public String name() { return "PaperGlobalYml"; }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path file = ctx.serverRoot().resolve("config").resolve("paper-global.yml").normalize();
        Files.createDirectories(file.getParent());

        // SnakeYAML dumper: force BLOCK style, sane indents (indicatorIndent < indent!)
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        opts.setIndicatorIndent(1);
        opts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Yaml yaml = new Yaml(opts);

        // Load current (comments are not preserved by SnakeYAML)
        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = yaml.load(raw);
            if (loaded instanceof Map<?, ?> m) root = toLinkedMap(m);
        }

        // ---- collisions ----
        set(root, "collisions.enable-player-collisions", false);
        set(root, "collisions.send-full-pos-for-hard-colliding-entities", true);

        // ---- messages ----
        set(root, "messages.kick.authentication-servers-down", "<lang:multiplayer.disconnect.authservers_down>");
        set(root, "messages.kick.connection-throttle", "Wacht even tot je opnieuw verbinding maakt.");
        set(root, "messages.kick.flying-player", "Vliegen is niet toegestaan op de server.");
        set(root, "messages.kick.flying-vehicle", "Vliegen is niet toegestaan op de server.");
        set(root, "messages.no-permission", "<red>Je mag dit commando niet uitvoeren.");
        set(root, "messages.use-display-name-in-quit-message", false);

        // ---- unsupported-settings ----
        set(root, "unsupported-settings.allow-headless-pistons", false);
        set(root, "unsupported-settings.allow-permanent-block-break-exploits", false);
        set(root, "unsupported-settings.allow-piston-duplication", false);
        set(root, "unsupported-settings.allow-unsafe-end-portal-teleportation", false);
        set(root, "unsupported-settings.compression-format", "ZLIB");
        set(root, "unsupported-settings.perform-username-validation", true);
        set(root, "unsupported-settings.skip-tripwire-hook-placement-validation", false);
        set(root, "unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked", false);
        set(root, "unsupported-settings.update-equipment-on-player-actions", true);

        // ---- watchdog ----
        set(root, "watchdog.early-warning-delay", 10_000);
        set(root, "watchdog.early-warning-every", 5_000);

        // ---- scoreboards ----
        set(root, "scoreboards.save-empty-scoreboard-teams", false);
        set(root, "scoreboards.track-plugin-scoreboards", false);

        // ---- proxies ----
        set(root, "proxies.bungee-cord.online-mode", true);
        set(root, "proxies.proxy-protocol", false);
        set(root, "proxies.velocity.enabled", true);
        set(root, "proxies.velocity.online-mode", true);
        set(root, "proxies.velocity.secret", "VO4m9zTY6dX6");

        // ---- spam-limiter ----
        set(root, "spam-limiter.incoming-packet-threshold", 300);
        set(root, "spam-limiter.recipe-spam-increment", 1);
        set(root, "spam-limiter.recipe-spam-limit", 20);
        set(root, "spam-limiter.tab-spam-increment", 1);
        set(root, "spam-limiter.tab-spam-limit", 500);

        // ---- spark ----
        set(root, "spark.enable-immediately", false);
        set(root, "spark.enabled", true);

        // ---- player-auto-save ----
        set(root, "player-auto-save.max-per-tick", -1);
        set(root, "player-auto-save.rate", -1);

        // ---- misc ----
        set(root, "misc.chat-threads.chat-executor-core-size", -1);
        set(root, "misc.chat-threads.chat-executor-max-size", -1);
        set(root, "misc.client-interaction-leniency-distance", "default");
        set(root, "misc.compression-level", "default");
        set(root, "misc.load-permissions-yml-before-plugins", true);
        set(root, "misc.max-joins-per-tick", 5);
        set(root, "misc.prevent-negative-villager-demand", false);
        set(root, "misc.region-file-cache-size", 256);
        set(root, "misc.send-full-pos-for-item-entities", false);
        set(root, "misc.strict-advancement-dimension-check", false);
        set(root, "misc.use-alternative-luck-formula", false);
        set(root, "misc.use-dimension-type-for-custom-spawners", false);
        set(root, "misc.xp-orb-groups-per-area", "default");

        // ---- commands ----
        set(root, "commands.ride-command-allow-player-as-vehicle", false);
        set(root, "commands.suggest-player-names-when-null-tab-completions", true);
        set(root, "commands.time-command-affects-all-worlds", false);

        // ---- console ----
        set(root, "console.enable-brigadier-completions", true);
        set(root, "console.enable-brigadier-highlighting", true);
        set(root, "console.has-all-permissions", false);

        // ---- item-validation ----
        set(root, "item-validation.book.author", 8192);
        set(root, "item-validation.book.page", 16384);
        set(root, "item-validation.book.title", 8192);
        set(root, "item-validation.book-size.page-max", 2560);
        set(root, "item-validation.book-size.total-multiplier", 0.98D);
        set(root, "item-validation.display-name", 8192);
        set(root, "item-validation.lore-line", 8192);
        set(root, "item-validation.resolve-selectors-in-books", false);

        // ---- logging ----
        set(root, "logging.deobfuscate-stacktraces", true);

        // ---- anticheat ----
        set(root, "anticheat.obfuscation.items.all-models.also-obfuscate", Collections.emptyList());
        ensureExactList(root, "anticheat.obfuscation.items.all-models.dont-obfuscate",
                List.of("minecraft:lodestone_tracker"));
        set(root, "anticheat.obfuscation.items.all-models.sanitize-count", true);
        set(root, "anticheat.obfuscation.items.enable-item-obfuscation", false);
        set(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.also-obfuscate", Collections.emptyList());
        ensureExactList(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.dont-obfuscate",
                List.of("minecraft:damage"));
        set(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.sanitize-count", true);

        // ---- block-updates ----
        set(root, "block-updates.disable-chorus-plant-updates", false);
        set(root, "block-updates.disable-mushroom-block-updates", false);
        set(root, "block-updates.disable-noteblock-updates", false);
        set(root, "block-updates.disable-tripwire-updates", false);

        // ---- chunk-loading-advanced ----
        set(root, "chunk-loading-advanced.auto-config-send-distance", true);
        set(root, "chunk-loading-advanced.player-max-concurrent-chunk-generates", 0);
        set(root, "chunk-loading-advanced.player-max-concurrent-chunk-loads", 0);

        // ---- chunk-loading-basic ----
        set(root, "chunk-loading-basic.player-max-chunk-generate-rate", -1.0D);
        set(root, "chunk-loading-basic.player-max-chunk-load-rate", 100.0D);
        set(root, "chunk-loading-basic.player-max-chunk-send-rate", 75.0D);

        // ---- chunk-system ----
        set(root, "chunk-system.gen-parallelism", "default");
        set(root, "chunk-system.io-threads", -1);
        set(root, "chunk-system.worker-threads", -1);

        // Build header + dump
        String header = makeHeader(root);
        String dumped = yaml.dump(root).trim() + "\n";
        String newContent = header + dumped;

        String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        if (!normalize(current).equals(normalize(newContent))) {
            Files.writeString(file, newContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return SanitizeResult.changed("paper-global.yml updated (enforced keys applied).");
        }
        return SanitizeResult.unchanged("paper-global.yml already compliant.");
    }

    /* ---------------- helpers ---------------- */

    private static String makeHeader(Map<String, Object> root) {
        List<String> unknown = new ArrayList<>();
        for (String k : root.keySet()) if (!KNOWN_TOP.contains(k)) unknown.add(k);

        StringBuilder sb = new StringBuilder();
        sb.append("# Managed by HauntedMC Sanitize (paper-global.yml)\n");
        if (!unknown.isEmpty()) {
            sb.append("#\n# --- new/deprecated/other (detected) ---\n");
            for (String u : unknown) sb.append("# - ").append(u).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static Map<String, Object> ensureSubMap(Map<String, Object> parent, String key) {
        Object next = parent.get(key);
        if (!(next instanceof Map<?, ?>)) {
            Map<String, Object> created = new LinkedHashMap<>();
            parent.put(key, created);
            return created;
        }
        Map<String, Object> linked = toLinkedMap((Map<?, ?>) next); // ensures LinkedHashMap
        if (next != linked) {
            parent.put(key, linked); // <-- replace reference in parent
        }
        return linked;
    }


    private static void set(Map<String, Object> root, String dottedPath, Object value) {
        String[] parts = dottedPath.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) {
            m = ensureSubMap(m, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        Object want = cloneForYaml(value);
        Object cur  = m.get(leaf);
        if (!Objects.equals(cur, want)) {
            m.put(leaf, want);
        }
    }

    private static void ensureExactList(Map<String, Object> root, String dottedPath, List<?> desired) {
        String[] parts = dottedPath.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) {
            m = ensureSubMap(m, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        Object cur = m.get(leaf);
        if (cur instanceof List<?> l && l.size() == desired.size()) {
            for (int i = 0; i < l.size(); i++) {
                if (!Objects.equals(l.get(i), desired.get(i))) {
                    m.put(leaf, new ArrayList<>(desired));
                    return;
                }
            }
            return;
        }
        m.put(leaf, new ArrayList<>(desired));
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
