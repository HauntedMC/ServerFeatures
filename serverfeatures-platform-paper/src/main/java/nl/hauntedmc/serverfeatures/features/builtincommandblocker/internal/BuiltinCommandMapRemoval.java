package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import org.bukkit.command.Command;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginIdentifiableCommand;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class BuiltinCommandMapRemoval {

    private BuiltinCommandMapRemoval() {
    }

    static boolean pruneInvalidRemovedCommands(
            Map<String, Command> removedCommands,
            Set<String> configuredServerAliases
    ) {
        return removedCommands.entrySet().removeIf(entry ->
                !isRestorable(entry.getKey(), entry.getValue(), configuredServerAliases)
        );
    }

    static Map<String, Command> effectiveCommands(
            Map<String, Command> liveCommands,
            Map<String, Command> removedCommands,
            Set<String> configuredServerAliases
    ) {
        LinkedHashMap<String, Command> effective = new LinkedHashMap<>(liveCommands);
        removedCommands.forEach((label, command) -> {
            if (isRestorable(label, command, configuredServerAliases) && !effective.containsKey(label)) {
                effective.put(label, command);
            }
        });
        return effective;
    }

    static boolean reconcile(
            Map<String, Command> liveCommands,
            Map<String, Command> removedCommands,
            Set<String> blockedCommands,
            Set<String> configuredServerAliases
    ) {
        boolean changed = false;
        Iterator<Map.Entry<String, Command>> iterator = removedCommands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Command> entry = iterator.next();
            String label = entry.getKey();
            if (!isRestorable(label, entry.getValue(), configuredServerAliases)) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (blockedCommands.contains(label)) {
                continue;
            }
            if (!liveCommands.containsKey(label)) {
                liveCommands.put(label, entry.getValue());
                changed = true;
            }
            iterator.remove();
        }

        for (String label : blockedCommands) {
            Command current = liveCommands.get(label);
            if (current == null) {
                continue;
            }
            Command removed = liveCommands.remove(label);
            if (removed != null) {
                removedCommands.put(label, removed);
                changed = true;
            }
        }
        return changed;
    }

    static boolean restoreAll(
            Map<String, Command> liveCommands,
            Map<String, Command> removedCommands,
            Set<String> configuredServerAliases
    ) {
        boolean changed = false;
        for (Map.Entry<String, Command> entry : removedCommands.entrySet()) {
            if (isRestorable(entry.getKey(), entry.getValue(), configuredServerAliases)
                    && !liveCommands.containsKey(entry.getKey())) {
                liveCommands.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        removedCommands.clear();
        return changed;
    }

    private static boolean isRestorable(
            String label,
            Command command,
            Set<String> configuredServerAliases
    ) {
        if (command instanceof FormattedCommandAlias) {
            return configuredServerAliases.contains(BuiltinCommandBlockerSettings.normalizeCommand(label));
        }
        return !(command instanceof PluginIdentifiableCommand identifiable)
                || identifiable.getPlugin().isEnabled();
    }
}
