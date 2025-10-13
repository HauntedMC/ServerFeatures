package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.util.YamlSanitizeUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

        Yaml yaml = YamlSanitizeUtil.newYaml();

        // Load current (comments are not preserved by SnakeYAML)
        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = yaml.load(raw);
            if (loaded instanceof Map<?, ?> m) root = YamlSanitizeUtil.toLinkedMap(m);
        }

        // We'll collect all controlled paths we set, to annotate them inline in the YAML output.
        LinkedHashSet<String> CONTROLLED = new LinkedHashSet<>();

        // ---- collisions ----
        YamlSanitizeUtil.set(root, "collisions.enable-player-collisions", false); CONTROLLED.add("collisions.enable-player-collisions");
        YamlSanitizeUtil.set(root, "collisions.send-full-pos-for-hard-colliding-entities", true); CONTROLLED.add("collisions.send-full-pos-for-hard-colliding-entities");

        // ---- messages ----
        YamlSanitizeUtil.set(root, "messages.kick.authentication-servers-down", "<lang:multiplayer.disconnect.authservers_down>"); CONTROLLED.add("messages.kick.authentication-servers-down");
        YamlSanitizeUtil.set(root, "messages.kick.connection-throttle", "Wacht even tot je opnieuw verbinding maakt."); CONTROLLED.add("messages.kick.connection-throttle");
        YamlSanitizeUtil.set(root, "messages.kick.flying-player", "Vliegen is niet toegestaan op de server."); CONTROLLED.add("messages.kick.flying-player");
        YamlSanitizeUtil.set(root, "messages.kick.flying-vehicle", "Vliegen is niet toegestaan op de server."); CONTROLLED.add("messages.kick.flying-vehicle");
        YamlSanitizeUtil.set(root, "messages.no-permission", "<red>Je mag dit commando niet uitvoeren."); CONTROLLED.add("messages.no-permission");
        YamlSanitizeUtil.set(root, "messages.use-display-name-in-quit-message", false); CONTROLLED.add("messages.use-display-name-in-quit-message");

        // ---- unsupported-settings ----
        YamlSanitizeUtil.set(root, "unsupported-settings.allow-headless-pistons", false); CONTROLLED.add("unsupported-settings.allow-headless-pistons");
        YamlSanitizeUtil.set(root, "unsupported-settings.allow-permanent-block-break-exploits", false); CONTROLLED.add("unsupported-settings.allow-permanent-block-break-exploits");
        YamlSanitizeUtil.set(root, "unsupported-settings.allow-piston-duplication", false); CONTROLLED.add("unsupported-settings.allow-piston-duplication");
        YamlSanitizeUtil.set(root, "unsupported-settings.allow-unsafe-end-portal-teleportation", false); CONTROLLED.add("unsupported-settings.allow-unsafe-end-portal-teleportation");
        YamlSanitizeUtil.set(root, "unsupported-settings.compression-format", "ZLIB"); CONTROLLED.add("unsupported-settings.compression-format");
        YamlSanitizeUtil.set(root, "unsupported-settings.perform-username-validation", true); CONTROLLED.add("unsupported-settings.perform-username-validation");
        YamlSanitizeUtil.set(root, "unsupported-settings.skip-tripwire-hook-placement-validation", false); CONTROLLED.add("unsupported-settings.skip-tripwire-hook-placement-validation");
        YamlSanitizeUtil.set(root, "unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked", false); CONTROLLED.add("unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked");
        YamlSanitizeUtil.set(root, "unsupported-settings.update-equipment-on-player-actions", true); CONTROLLED.add("unsupported-settings.update-equipment-on-player-actions");

        // ---- watchdog ----
        YamlSanitizeUtil.set(root, "watchdog.early-warning-delay", 10_000); CONTROLLED.add("watchdog.early-warning-delay");
        YamlSanitizeUtil.set(root, "watchdog.early-warning-every", 5_000); CONTROLLED.add("watchdog.early-warning-every");

        // ---- scoreboards ----
        YamlSanitizeUtil.set(root, "scoreboards.save-empty-scoreboard-teams", false); CONTROLLED.add("scoreboards.save-empty-scoreboard-teams");
        YamlSanitizeUtil.set(root, "scoreboards.track-plugin-scoreboards", false); CONTROLLED.add("scoreboards.track-plugin-scoreboards");

        // ---- proxies ----
        YamlSanitizeUtil.set(root, "proxies.bungee-cord.online-mode", true); CONTROLLED.add("proxies.bungee-cord.online-mode");
        YamlSanitizeUtil.set(root, "proxies.proxy-protocol", false); CONTROLLED.add("proxies.proxy-protocol");
        YamlSanitizeUtil.set(root, "proxies.velocity.enabled", true); CONTROLLED.add("proxies.velocity.enabled");
        YamlSanitizeUtil.set(root, "proxies.velocity.online-mode", true); CONTROLLED.add("proxies.velocity.online-mode");
        YamlSanitizeUtil.set(root, "proxies.velocity.secret", "VO4m9zTY6dX6"); CONTROLLED.add("proxies.velocity.secret");

        // ---- spam-limiter ----
        YamlSanitizeUtil.set(root, "spam-limiter.incoming-packet-threshold", 300); CONTROLLED.add("spam-limiter.incoming-packet-threshold");
        YamlSanitizeUtil.set(root, "spam-limiter.recipe-spam-increment", 1); CONTROLLED.add("spam-limiter.recipe-spam-increment");
        YamlSanitizeUtil.set(root, "spam-limiter.recipe-spam-limit", 20); CONTROLLED.add("spam-limiter.recipe-spam-limit");
        YamlSanitizeUtil.set(root, "spam-limiter.tab-spam-increment", 1); CONTROLLED.add("spam-limiter.tab-spam-increment");
        YamlSanitizeUtil.set(root, "spam-limiter.tab-spam-limit", 500); CONTROLLED.add("spam-limiter.tab-spam-limit");

        // ---- spark ----
        YamlSanitizeUtil.set(root, "spark.enable-immediately", false); CONTROLLED.add("spark.enable-immediately");
        YamlSanitizeUtil.set(root, "spark.enabled", true); CONTROLLED.add("spark.enabled");

        // ---- player-auto-save ----
        YamlSanitizeUtil.set(root, "player-auto-save.max-per-tick", -1); CONTROLLED.add("player-auto-save.max-per-tick");
        YamlSanitizeUtil.set(root, "player-auto-save.rate", -1); CONTROLLED.add("player-auto-save.rate");

        // ---- misc ----
        YamlSanitizeUtil.set(root, "misc.chat-threads.chat-executor-core-size", -1); CONTROLLED.add("misc.chat-threads.chat-executor-core-size");
        YamlSanitizeUtil.set(root, "misc.chat-threads.chat-executor-max-size", -1); CONTROLLED.add("misc.chat-threads.chat-executor-max-size");
        YamlSanitizeUtil.set(root, "misc.client-interaction-leniency-distance", "default"); CONTROLLED.add("misc.client-interaction-leniency-distance");
        YamlSanitizeUtil.set(root, "misc.compression-level", "default"); CONTROLLED.add("misc.compression-level");
        YamlSanitizeUtil.set(root, "misc.load-permissions-yml-before-plugins", true); CONTROLLED.add("misc.load-permissions-yml-before-plugins");
        YamlSanitizeUtil.set(root, "misc.max-joins-per-tick", 5); CONTROLLED.add("misc.max-joins-per-tick");
        YamlSanitizeUtil.set(root, "misc.prevent-negative-villager-demand", false); CONTROLLED.add("misc.prevent-negative-villager-demand");
        YamlSanitizeUtil.set(root, "misc.region-file-cache-size", 256); CONTROLLED.add("misc.region-file-cache-size");
        YamlSanitizeUtil.set(root, "misc.send-full-pos-for-item-entities", false); CONTROLLED.add("misc.send-full-pos-for-item-entities");
        YamlSanitizeUtil.set(root, "misc.strict-advancement-dimension-check", false); CONTROLLED.add("misc.strict-advancement-dimension-check");
        YamlSanitizeUtil.set(root, "misc.use-alternative-luck-formula", false); CONTROLLED.add("misc.use-alternative-luck-formula");
        YamlSanitizeUtil.set(root, "misc.use-dimension-type-for-custom-spawners", false); CONTROLLED.add("misc.use-dimension-type-for-custom-spawners");
        YamlSanitizeUtil.set(root, "misc.xp-orb-groups-per-area", "default"); CONTROLLED.add("misc.xp-orb-groups-per-area");

        // ---- commands ----
        YamlSanitizeUtil.set(root, "commands.ride-command-allow-player-as-vehicle", false); CONTROLLED.add("commands.ride-command-allow-player-as-vehicle");
        YamlSanitizeUtil.set(root, "commands.suggest-player-names-when-null-tab-completions", true); CONTROLLED.add("commands.suggest-player-names-when-null-tab-completions");
        YamlSanitizeUtil.set(root, "commands.time-command-affects-all-worlds", false); CONTROLLED.add("commands.time-command-affects-all-worlds");

        // ---- console ----
        YamlSanitizeUtil.set(root, "console.enable-brigadier-completions", true); CONTROLLED.add("console.enable-brigadier-completions");
        YamlSanitizeUtil.set(root, "console.enable-brigadier-highlighting", true); CONTROLLED.add("console.enable-brigadier-highlighting");
        YamlSanitizeUtil.set(root, "console.has-all-permissions", false); CONTROLLED.add("console.has-all-permissions");

        // ---- item-validation ----
        YamlSanitizeUtil.set(root, "item-validation.book.author", 8192); CONTROLLED.add("item-validation.book.author");
        YamlSanitizeUtil.set(root, "item-validation.book.page", 16384); CONTROLLED.add("item-validation.book.page");
        YamlSanitizeUtil.set(root, "item-validation.book.title", 8192); CONTROLLED.add("item-validation.book.title");
        YamlSanitizeUtil.set(root, "item-validation.book-size.page-max", 2560); CONTROLLED.add("item-validation.book-size.page-max");
        YamlSanitizeUtil.set(root, "item-validation.book-size.total-multiplier", 0.98D); CONTROLLED.add("item-validation.book-size.total-multiplier");
        YamlSanitizeUtil.set(root, "item-validation.display-name", 8192); CONTROLLED.add("item-validation.display-name");
        YamlSanitizeUtil.set(root, "item-validation.lore-line", 8192); CONTROLLED.add("item-validation.lore-line");
        YamlSanitizeUtil.set(root, "item-validation.resolve-selectors-in-books", false); CONTROLLED.add("item-validation.resolve-selectors-in-books");

        // ---- logging ----
        YamlSanitizeUtil.set(root, "logging.deobfuscate-stacktraces", true); CONTROLLED.add("logging.deobfuscate-stacktraces");

        // ---- anticheat ----
        YamlSanitizeUtil.set(root, "anticheat.obfuscation.items.all-models.also-obfuscate", Collections.emptyList()); CONTROLLED.add("anticheat.obfuscation.items.all-models.also-obfuscate");
        YamlSanitizeUtil.ensureExactList(root, "anticheat.obfuscation.items.all-models.dont-obfuscate",
                List.of("minecraft:lodestone_tracker")); CONTROLLED.add("anticheat.obfuscation.items.all-models.dont-obfuscate");
        YamlSanitizeUtil.set(root, "anticheat.obfuscation.items.all-models.sanitize-count", true); CONTROLLED.add("anticheat.obfuscation.items.all-models.sanitize-count");
        YamlSanitizeUtil.set(root, "anticheat.obfuscation.items.enable-item-obfuscation", false); CONTROLLED.add("anticheat.obfuscation.items.enable-item-obfuscation");
        YamlSanitizeUtil.set(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.also-obfuscate", Collections.emptyList()); CONTROLLED.add("anticheat.obfuscation.items.model-overrides.minecraft:elytra.also-obfuscate");
        YamlSanitizeUtil.ensureExactList(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.dont-obfuscate",
                List.of("minecraft:damage")); CONTROLLED.add("anticheat.obfuscation.items.model-overrides.minecraft:elytra.dont-obfuscate");
        YamlSanitizeUtil.set(root, "anticheat.obfuscation.items.model-overrides.minecraft:elytra.sanitize-count", true); CONTROLLED.add("anticheat.obfuscation.items.model-overrides.minecraft:elytra.sanitize-count");

        // ---- block-updates ----
        YamlSanitizeUtil.set(root, "block-updates.disable-chorus-plant-updates", false); CONTROLLED.add("block-updates.disable-chorus-plant-updates");
        YamlSanitizeUtil.set(root, "block-updates.disable-mushroom-block-updates", false); CONTROLLED.add("block-updates.disable-mushroom-block-updates");
        YamlSanitizeUtil.set(root, "block-updates.disable-noteblock-updates", false); CONTROLLED.add("block-updates.disable-noteblock-updates");
        YamlSanitizeUtil.set(root, "block-updates.disable-tripwire-updates", false); CONTROLLED.add("block-updates.disable-tripwire-updates");

        // ---- chunk-loading-advanced ----
        YamlSanitizeUtil.set(root, "chunk-loading-advanced.auto-config-send-distance", true); CONTROLLED.add("chunk-loading-advanced.auto-config-send-distance");
        YamlSanitizeUtil.set(root, "chunk-loading-advanced.player-max-concurrent-chunk-generates", 0); CONTROLLED.add("chunk-loading-advanced.player-max-concurrent-chunk-generates");
        YamlSanitizeUtil.set(root, "chunk-loading-advanced.player-max-concurrent-chunk-loads", 0); CONTROLLED.add("chunk-loading-advanced.player-max-concurrent-chunk-loads");

        // ---- chunk-loading-basic ----
        YamlSanitizeUtil.set(root, "chunk-loading-basic.player-max-chunk-generate-rate", -1.0D); CONTROLLED.add("chunk-loading-basic.player-max-chunk-generate-rate");
        YamlSanitizeUtil.set(root, "chunk-loading-basic.player-max-chunk-load-rate", 100.0D); CONTROLLED.add("chunk-loading-basic.player-max-chunk-load-rate");
        YamlSanitizeUtil.set(root, "chunk-loading-basic.player-max-chunk-send-rate", 75.0D); CONTROLLED.add("chunk-loading-basic.player-max-chunk-send-rate");

        // ---- chunk-system ----
        YamlSanitizeUtil.set(root, "chunk-system.gen-parallelism", "default"); CONTROLLED.add("chunk-system.gen-parallelism");
        YamlSanitizeUtil.set(root, "chunk-system.io-threads", -1); CONTROLLED.add("chunk-system.io-threads");
        YamlSanitizeUtil.set(root, "chunk-system.worker-threads", -1); CONTROLLED.add("chunk-system.worker-threads");

        // Build header + dump
        String header = makeHeader(root);
        String dumpedRaw = yaml.dump(root).trim() + "\n";
        String dumped = YamlSanitizeUtil.appendControlComments(dumpedRaw, CONTROLLED);
        String newContent = header + dumped;

        String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        if (!YamlSanitizeUtil.normalize(current).equals(YamlSanitizeUtil.normalize(newContent))) {
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
        sb.append("# Controlled entries are annotated inline with \" # controlled by Sanitize\".\n\n");
        return sb.toString();
    }
}
