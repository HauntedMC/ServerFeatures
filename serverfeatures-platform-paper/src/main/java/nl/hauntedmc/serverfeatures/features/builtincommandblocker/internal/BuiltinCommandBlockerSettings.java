package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record BuiltinCommandBlockerSettings(
        Set<BuiltinCommandSource> blockedSources,
        boolean blockLegacyAliases,
        boolean removeFromCommandMap,
        Set<String> allowedCommands
) {

    public BuiltinCommandBlockerSettings {
        blockedSources = blockedSources.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(blockedSources));
        allowedCommands = Set.copyOf(allowedCommands);
    }

    public static BuiltinCommandBlockerSettings load(FeatureConfigHandler config) {
        EnumSet<BuiltinCommandSource> blockedSources = EnumSet.noneOf(BuiltinCommandSource.class);
        for (BuiltinCommandSource source : BuiltinCommandSource.values()) {
            if (config.get("block." + source.configKey(), Boolean.class, true)) {
                blockedSources.add(source);
            }
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String value : config.getList("allowed", String.class, List.of())) {
            String normalized = normalizeCommand(value);
            if (!normalized.isEmpty()) {
                allowed.add(normalized);
            }
        }

        return new BuiltinCommandBlockerSettings(
                blockedSources,
                config.get("block.legacy_aliases", Boolean.class, true),
                config.get("remove_from_command_map", Boolean.class, false),
                allowed
        );
    }

    public boolean blocks(BuiltinCommandSource source) {
        return blockedSources.contains(source);
    }

    public boolean allows(String command) {
        return allowedCommands.contains(normalizeCommand(command));
    }

    public boolean allows(BuiltinCommandSource source, String command) {
        String normalized = normalizeCommand(command);
        if (normalized.isEmpty()) {
            return false;
        }
        if (allowedCommands.contains(normalized)) {
            return true;
        }

        String terminalLabel = terminalLabel(normalized);
        for (String allowed : allowedCommands) {
            if (!terminalLabel(allowed).equals(terminalLabel)) {
                continue;
            }
            int colon = allowed.indexOf(':');
            if (colon < 0) {
                return true;
            }
            BuiltinCommandSource allowedSource = BuiltinCommandSource.fromNamespace(allowed.substring(0, colon));
            if (allowedSource == source) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String terminalLabel(String command) {
        int colon = command.lastIndexOf(':');
        return colon < 0 ? command : command.substring(colon + 1);
    }
}
