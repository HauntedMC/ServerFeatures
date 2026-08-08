package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BuiltinCommandDiscovery {

    private static final String LEGACY_ALIASES = "legacy_aliases";
    private static final String BUNDLED_SPARK_COMMAND_CLASS = "io.papermc.paper.sparksfly$commandimpl";

    private BuiltinCommandDiscovery() {
    }

    public static BuiltinCommandSnapshot discover(
            Map<String, Command> knownCommands,
            BuiltinCommandBlockerSettings settings
    ) {
        IdentityHashMap<Command, List<String>> registrations = new IdentityHashMap<>();
        knownCommands.forEach((key, command) -> {
            if (key != null && command != null) {
                registrations.computeIfAbsent(command, ignored -> new ArrayList<>())
                        .add(BuiltinCommandBlockerSettings.normalizeCommand(key));
            }
        });

        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> detectedSources = emptySourceCounts();

        registrations.forEach((command, keys) -> {
            BuiltinCommandSource source = classify(command, keys);
            if (source == null || !settings.blocks(source) || isAllowed(command, keys, source, settings)) {
                return;
            }

            for (String key : keys) {
                if (key.isEmpty()) {
                    continue;
                }
                boolean alias = isAliasRegistration(command, key);
                if (alias && !settings.blockLegacyAliases()) {
                    continue;
                }
                if (blocked.add(key)) {
                    detectedSources.compute(
                            source.configKey(),
                            (ignored, count) -> count == null ? 1 : count + 1
                    );
                    if (alias) {
                        detectedSources.compute(
                                LEGACY_ALIASES,
                                (ignored, count) -> count == null ? 1 : count + 1
                        );
                    }
                }
            }
        });

        return new BuiltinCommandSnapshot(blocked, detectedSources);
    }

    static BuiltinCommandSource classify(Command command, Collection<String> registrationKeys) {
        String className = command.getClass().getName().toLowerCase(Locale.ROOT);

        // Modern Paper command registrations are exposed through PluginVanillaCommandWrapper, which lives in
        // io.papermc.paper.*. Plugin ownership therefore has to win over implementation-package heuristics.
        if (command instanceof PluginIdentifiableCommand identifiable) {
            String pluginName = identifiable.getPlugin().getName().toLowerCase(Locale.ROOT);
            if (pluginName.equals("spark") || pluginName.equals("spark-paper")) {
                return BuiltinCommandSource.SPARK;
            }
            return sourceFromRegistrationNamespace(registrationKeys);
        }

        if (isBundledSparkCommand(command, className)) {
            return BuiltinCommandSource.SPARK;
        }
        if (className.startsWith("org.bukkit.command.defaults.")) {
            return BuiltinCommandSource.BUKKIT;
        }
        if (className.startsWith("org.spigotmc.")) {
            return BuiltinCommandSource.SPIGOT;
        }
        if (className.startsWith("io.papermc.paper.") || className.startsWith("com.destroystokyo.paper.")) {
            return BuiltinCommandSource.PAPER;
        }

        BuiltinCommandSource namespacedSource = sourceFromRegistrationNamespace(registrationKeys);
        if (namespacedSource != null) {
            return namespacedSource;
        }
        if (className.startsWith("net.minecraft.") || className.contains("vanillacommandwrapper")) {
            return BuiltinCommandSource.MINECRAFT;
        }
        return null;
    }

    static boolean isAliasRegistration(Command command, String registrationKey) {
        String label = stripNamespaces(BuiltinCommandBlockerSettings.normalizeCommand(registrationKey));
        if (label.equals(BuiltinCommandBlockerSettings.normalizeCommand(command.getName()))
                || label.equals(BuiltinCommandBlockerSettings.normalizeCommand(command.getLabel()))) {
            return false;
        }

        for (String alias : command.getAliases()) {
            if (label.equals(BuiltinCommandBlockerSettings.normalizeCommand(alias))) {
                return true;
            }
        }

        // A second registration for the same Command instance that is not its canonical name is an alias,
        // even if the command implementation does not expose it through getAliases().
        return true;
    }

    private static boolean isBundledSparkCommand(Command command, String className) {
        if (className.equals(BUNDLED_SPARK_COMMAND_CLASS)
                || className.startsWith("me.lucko.spark.")
                || className.startsWith("org.papermc.spark.")) {
            return true;
        }
        return BuiltinCommandBlockerSettings.normalizeCommand(command.getName()).equals("spark")
                && command.getClass().getEnclosingClass() != null
                && command.getClass().getEnclosingClass().getName().equals("io.papermc.paper.SparksFly");
    }

    private static BuiltinCommandSource sourceFromRegistrationNamespace(Collection<String> registrationKeys) {
        for (String key : registrationKeys) {
            int colon = key.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            BuiltinCommandSource source = BuiltinCommandSource.fromNamespace(key.substring(0, colon));
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static boolean isAllowed(
            Command command,
            Collection<String> registrationKeys,
            BuiltinCommandSource source,
            BuiltinCommandBlockerSettings settings
    ) {
        if (settings.allows(source, command.getName()) || settings.allows(source, command.getLabel())) {
            return true;
        }
        for (String alias : command.getAliases()) {
            if (settings.allows(source, alias)) {
                return true;
            }
        }
        for (String key : registrationKeys) {
            if (settings.allows(source, key)) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespaces(String command) {
        int colon = command.lastIndexOf(':');
        return colon < 0 ? command : command.substring(colon + 1);
    }

    private static LinkedHashMap<String, Integer> emptySourceCounts() {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (BuiltinCommandSource source : BuiltinCommandSource.values()) {
            result.put(source.configKey(), 0);
        }
        result.put(LEGACY_ALIASES, 0);
        return result;
    }
}
