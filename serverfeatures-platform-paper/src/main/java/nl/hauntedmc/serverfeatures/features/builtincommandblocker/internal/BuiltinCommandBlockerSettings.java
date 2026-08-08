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
}
