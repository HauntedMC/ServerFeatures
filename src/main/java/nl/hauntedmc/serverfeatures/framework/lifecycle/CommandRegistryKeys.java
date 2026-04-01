package nl.hauntedmc.serverfeatures.framework.lifecycle;

import org.bukkit.command.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CommandRegistryKeys {

    private CommandRegistryKeys() {
    }

    static List<String> knownCommandKeys(String pluginName, String primaryName, List<String> aliases) {
        String nsPrefix = pluginName.toLowerCase(Locale.ROOT) + ":";
        String primary = primaryName.toLowerCase(Locale.ROOT);

        List<String> keys = new ArrayList<>();
        keys.add(primary);
        keys.add(nsPrefix + primary);

        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            keys.add(normalizedAlias);
            keys.add(nsPrefix + normalizedAlias);
        }

        return keys;
    }

    static void purgeKnownCommands(Map<String, Command> knownCommands, Command command, List<String> keys) {
        for (String key : keys) {
            Command mapped = knownCommands.get(key);
            if (mapped == command) {
                knownCommands.remove(key);
            }
        }

        // Safety net: remove any remaining entries mapped to the command instance.
        knownCommands.entrySet().removeIf(e -> e.getValue() == command);
    }
}
