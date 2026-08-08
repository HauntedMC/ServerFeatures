package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import io.papermc.paper.testing.PluginOwnedPaperWrapperCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinCommandMapRemovalTest {

    @Test
    void removesAndRestoresExactBlockedRegistrations() {
        Command give = new FakeCommand("give");
        Map<String, Command> live = new LinkedHashMap<>();
        live.put("give", give);
        live.put("minecraft:give", give);
        Map<String, Command> removed = new LinkedHashMap<>();

        assertTrue(BuiltinCommandMapRemoval.reconcile(
                live,
                removed,
                Set.of("give", "minecraft:give"),
                Set.of()
        ));
        assertTrue(live.isEmpty());
        assertEquals(Set.of("give", "minecraft:give"), removed.keySet());

        assertTrue(BuiltinCommandMapRemoval.restoreAll(live, removed, Set.of()));
        assertSame(give, live.get("give"));
        assertSame(give, live.get("minecraft:give"));
        assertTrue(removed.isEmpty());
    }

    @Test
    void effectiveCommandsKeepRemovedEntriesDiscoverable() {
        Command help = new FakeCommand("help");
        Map<String, Command> live = new LinkedHashMap<>();
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("bukkit:help", help);

        Map<String, Command> effective = BuiltinCommandMapRemoval.effectiveCommands(live, removed, Set.of());

        assertSame(help, effective.get("bukkit:help"));
        assertFalse(live.containsKey("bukkit:help"));
    }

    @Test
    void restoringDoesNotOverwriteAReplacementCommand() {
        Command oldBuiltin = new FakeCommand("help");
        Command replacement = new FakeCommand("help");
        Map<String, Command> live = new LinkedHashMap<>();
        live.put("help", replacement);
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("help", oldBuiltin);

        assertFalse(BuiltinCommandMapRemoval.restoreAll(live, removed, Set.of()));

        assertSame(replacement, live.get("help"));
        assertTrue(removed.isEmpty());
    }

    @Test
    void reconcileDropsStaleRemovedEntryWhenReplacementOwnsTheLabel() {
        Command oldBuiltin = new FakeCommand("paper");
        Command replacement = new FakeCommand("paper");
        Map<String, Command> live = new LinkedHashMap<>();
        live.put("paper", replacement);
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("paper", oldBuiltin);

        assertFalse(BuiltinCommandMapRemoval.reconcile(live, removed, Set.of(), Set.of()));

        assertSame(replacement, live.get("paper"));
        assertTrue(removed.isEmpty());
    }

    @Test
    void disabledPluginCommandsAreNeverRestored() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(false);
        Command stale = new PluginOwnedPaperWrapperCommand("paperthing", plugin);
        Map<String, Command> live = new LinkedHashMap<>();
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("paper:paperthing", stale);

        assertTrue(BuiltinCommandMapRemoval.pruneInvalidRemovedCommands(removed, Set.of()));
        assertTrue(removed.isEmpty());
        assertFalse(BuiltinCommandMapRemoval.restoreAll(live, removed, Set.of()));
        assertTrue(live.isEmpty());
    }

    @Test
    void deletedCommandsYmlAliasesAreNeverRestored() {
        Command staleAlias = mock(FormattedCommandAlias.class);
        Map<String, Command> live = new LinkedHashMap<>();
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("old-alias", staleAlias);

        assertTrue(BuiltinCommandMapRemoval.pruneInvalidRemovedCommands(removed, Set.of()));
        assertTrue(removed.isEmpty());
        assertFalse(BuiltinCommandMapRemoval.restoreAll(live, removed, Set.of()));
        assertTrue(live.isEmpty());
    }

    @Test
    void configuredCommandsYmlAliasesRemainRestorable() {
        Command alias = mock(FormattedCommandAlias.class);
        Map<String, Command> live = new LinkedHashMap<>();
        Map<String, Command> removed = new LinkedHashMap<>();
        removed.put("kept-alias", alias);

        assertFalse(BuiltinCommandMapRemoval.pruneInvalidRemovedCommands(removed, Set.of("kept-alias")));
        assertTrue(BuiltinCommandMapRemoval.restoreAll(live, removed, Set.of("kept-alias")));
        assertSame(alias, live.get("kept-alias"));
    }

    private static final class FakeCommand extends Command {

        private FakeCommand(String name) {
            super(name);
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
