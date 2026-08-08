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
            if (source == null || !settings.blocks(source) || isAllowed(command, keys, settings)) {
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

        if (isSparkCommand(command, className)) {
            return BuiltinCommandSource.SPARK;
        }
        if (className.startsWith("io.papermc.paper.") || className.startsWith("com.destroystokyo.paper.")) {
            return BuiltinCommandSource.PAPER;
        }
        if (className.startsWith("org.spigotmc.")) {
            return BuiltinCommandSource.SPIGOT;
        }
        if (className.startsWith("net.minecraft.") || className.contains("vanillacommandwrapper")) {
            return BuiltinCommandSource.MINECRAFT;
        }
        if (className.startsWith("org.bukkit.command.defaults.")) {
            return BuiltinCommandSource.BUKKIT;
        }

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

    static boolean isAliasRegistration(Command command, String registrationKey) {
        String label = stripNamespace(BuiltinCommandBlockerSettings.normalizeCommand(registrationKey));
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

    private static boolean isSparkCommand(Command command, String className) {
        if (className.startsWith("me.lucko.spark.") || className.startsWith("org.papermc.spark.")) {
            return true;
        }
        if (command instanceof PluginIdentifiableCommand identifiable) {
            String pluginName = identifiable.getPlugin().getName().toLowerCase(Locale.ROOT);
            return pluginName.equals("spark") || pluginName.equals("spark-paper");
        }
        return false;
    }

    private static boolean isAllowed(
            Command command,
            Collection<String> registrationKeys,
            BuiltinCommandBlockerSettings settings
    ) {
        if (settings.allows(command.getName()) || settings.allows(command.getLabel())) {
            return true;
        }
        for (String alias : command.getAliases()) {
            if (settings.allows(alias)) {
                return true;
            }
        }
        for (String key : registrationKeys) {
            if (settings.allows(key)) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespace(String command) {
        int colon = command.indexOf(':');
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
