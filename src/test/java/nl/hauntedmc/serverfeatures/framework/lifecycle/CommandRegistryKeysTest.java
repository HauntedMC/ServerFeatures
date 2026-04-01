package nl.hauntedmc.serverfeatures.framework.lifecycle;

import org.bukkit.command.Command;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryKeysTest {

    @Test
    void knownCommandKeysBuildsPrimaryNamespacedAndAliasKeys() {
        List<String> aliases = new ArrayList<>();
        aliases.add("Alias");
        aliases.add("other");
        aliases.add("");
        aliases.add(null);
        List<String> keys = CommandRegistryKeys.knownCommandKeys(
                "ServerFeatures",
                "Main",
                aliases
        );

        assertEquals(
                List.of("main", "serverfeatures:main", "alias", "serverfeatures:alias", "other", "serverfeatures:other"),
                keys
        );
    }

    @Test
    void purgeKnownCommandsRemovesExplicitAndResidualMappings() {
        Command cmd = new DummyCommand("main");
        Command other = new DummyCommand("other");
        Map<String, Command> known = new LinkedHashMap<>();
        known.put("a", cmd);
        known.put("ns:a", cmd);
        known.put("alias", cmd);
        known.put("x", other);
        known.put("leftover", cmd);

        CommandRegistryKeys.purgeKnownCommands(known, cmd, List.of("a", "ns:a", "alias", "ns:alias"));

        assertFalse(known.containsKey("a"));
        assertFalse(known.containsKey("ns:a"));
        assertFalse(known.containsKey("alias"));
        assertFalse(known.containsKey("leftover"));
        assertTrue(known.containsKey("x"));
    }

    private static final class DummyCommand extends Command {
        private DummyCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }
}
