package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinCommandDiscoveryTest {

    @Test
    void blocksCanonicalNamespacedAndAliasRegistrations() {
        FakeCommand give = new FakeCommand("give", "g");
        Map<String, Command> commands = registrations(
                "give", give,
                "minecraft:give", give,
                "g", give,
                "minecraft:g", give
        );

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(commands, allBlocked(true, Set.of()));

        assertEquals(Set.of("give", "minecraft:give", "g", "minecraft:g"), snapshot.blockedCommands());
        assertEquals(4, snapshot.detectedSources().get("minecraft"));
        assertEquals(2, snapshot.detectedSources().get("legacy_aliases"));
    }

    @Test
    void legacyAliasesCanRemainAvailable() {
        FakeCommand give = new FakeCommand("give", "g");
        Map<String, Command> commands = registrations(
                "give", give,
                "minecraft:give", give,
                "g", give,
                "minecraft:g", give
        );

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(commands, allBlocked(false, Set.of()));

        assertEquals(Set.of("give", "minecraft:give"), snapshot.blockedCommands());
        assertEquals(0, snapshot.detectedSources().get("legacy_aliases"));
    }

    @Test
    void allowlistEntryAllowsTheWholeLogicalCommand() {
        FakeCommand give = new FakeCommand("give", "g");
        Map<String, Command> commands = registrations(
                "give", give,
                "minecraft:give", give,
                "g", give,
                "minecraft:g", give
        );

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(
                commands,
                allBlocked(true, Set.of("minecraft:give"))
        );

        assertTrue(snapshot.blockedCommands().isEmpty());
    }

    @Test
    void thirdPartyNamespacesAreNotBlocked() {
        FakeCommand custom = new FakeCommand("custom");
        Map<String, Command> commands = registrations(
                "custom", custom,
                "example:custom", custom
        );

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(commands, allBlocked(true, Set.of()));

        assertTrue(snapshot.blockedCommands().isEmpty());
    }

    @Test
    void eachBuiltinNamespaceCanBeDetectedIndependently() {
        Map<String, Command> commands = new LinkedHashMap<>();
        commands.put("minecraft:give", new FakeCommand("give"));
        commands.put("bukkit:help", new FakeCommand("help"));
        commands.put("paper:paper", new FakeCommand("paper"));
        commands.put("spigot:restart", new FakeCommand("restart"));
        commands.put("spark:spark", new FakeCommand("spark"));

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(commands, allBlocked(true, Set.of()));

        assertEquals(5, snapshot.blockedCommandCount());
        for (BuiltinCommandSource source : BuiltinCommandSource.values()) {
            assertEquals(1, snapshot.detectedSources().get(source.configKey()));
        }
    }

    @Test
    void disabledSourceIsNotBlocked() {
        EnumSet<BuiltinCommandSource> sources = EnumSet.allOf(BuiltinCommandSource.class);
        sources.remove(BuiltinCommandSource.PAPER);
        BuiltinCommandBlockerSettings settings = new BuiltinCommandBlockerSettings(sources, true, Set.of());

        BuiltinCommandSnapshot snapshot = BuiltinCommandDiscovery.discover(
                Map.of("paper:paper", new FakeCommand("paper")),
                settings
        );

        assertFalse(snapshot.isBlocked("paper:paper"));
    }

    @Test
    void commandLineRootIsNormalizedBeforeEnforcement() {
        assertEquals("minecraft:give", BuiltinCommandBlockerService.rootCommand(" /MiNeCrAfT:GiVe Player stone 1 "));
        assertEquals("plugins", BuiltinCommandBlockerService.rootCommand("/plugins"));
    }

    private static BuiltinCommandBlockerSettings allBlocked(boolean aliases, Set<String> allowed) {
        return new BuiltinCommandBlockerSettings(EnumSet.allOf(BuiltinCommandSource.class), aliases, allowed);
    }

    private static Map<String, Command> registrations(Object... entries) {
        LinkedHashMap<String, Command> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], (Command) entries[index + 1]);
        }
        return result;
    }

    private static final class FakeCommand extends Command {

        private FakeCommand(String name, String... aliases) {
            super(name);
            setAliases(List.of(aliases));
        }

        @Override
        public boolean execute(
                @NotNull CommandSender sender,
                @NotNull String commandLabel,
                @NotNull String @NotNull [] args
        ) {
            return true;
        }
    }
}
