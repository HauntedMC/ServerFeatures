package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import org.bukkit.command.Command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class BuiltinCommandMapRemoval {

    private BuiltinCommandMapRemoval() {
    }

    static Map<String, Command> effectiveCommands(
            Map<String, Command> liveCommands,
            Map<String, Command> removedCommands
    ) {
        LinkedHashMap<String, Command> effective = new LinkedHashMap<>(liveCommands);
        removedCommands.forEach((label, command) -> {
            if (!effective.containsKey(label)) {
                effective.put(label, command);
            }
        });
        return effective;
    }

    static void reconcile(
            Map<String, Command> liveCommands,
            Map<String, Command> removedCommands,
            Set<String> blockedCommands
    ) {
        removedCommands.entrySet().removeIf(entry -> {
            String label = entry.getKey();
            if (blockedCommands.contains(label)) {
                return false;
            }
            if (!liveCommands.containsKey(label)) {
                liveCommands.put(label, entry.getValue());
            }
            return true;
        });

        for (String label : blockedCommands) {
            Command current = liveCommands.get(label);
            if (current == null) {
                continue;
            }
            Command removed = liveCommands.remove(label);
            if (removed != null) {
                removedCommands.put(label, removed);
            }
        }
    }

    static void restoreAll(Map<String, Command> liveCommands, Map<String, Command> removedCommands) {
        removedCommands.forEach((label, command) -> {
            if (!liveCommands.containsKey(label)) {
                liveCommands.put(label, command);
            }
        });
        removedCommands.clear();
    }
}
