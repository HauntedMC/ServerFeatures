package nl.hauntedmc.serverfeatures.framework.lifecycle;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.serverfeatures.framework.command.brigadier.BrigadierDispatcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandRegistryTakeoverTest {

    @Test
    void blocksConflictsWithoutMutatingEitherRegistryWhenOverwriteIsDisabled() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        CommandNode<CommandSourceStack> root = root("god");
        knownCommands.put("god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        when(dispatcher.getRootLiteral("god")).thenReturn(root);

        CommandRegistryTakeover takeover = new CommandRegistryTakeover(commandMap, dispatcher);
        CommandRegistryTakeover.Claim claim = takeover.claim(List.of("god"), false);

        assertFalse(claim.claimed());
        assertEquals("god", claim.blockingConflict().label());
        assertSame(original, knownCommands.get("god"));
        verify(dispatcher).getRootLiteral("god");
    }

    @Test
    void removesOnlyUnnamespacedBindingsAndRestoresThemLater() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        CommandNode<CommandSourceStack> root = root("god");
        knownCommands.put("god", original);
        knownCommands.put("essentials:god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        when(dispatcher.getRootLiteral("god")).thenReturn(root);
        when(dispatcher.takeRootLiteral("god")).thenReturn(root);
        when(dispatcher.restoreRootLiteral("god", root)).thenReturn(true);

        CommandRegistryTakeover takeover = new CommandRegistryTakeover(commandMap, dispatcher);
        CommandRegistryTakeover.Claim claim = takeover.claim(List.of("god"), true);

        assertTrue(claim.claimed());
        assertNull(knownCommands.get("god"));
        assertSame(original, knownCommands.get("essentials:god"));
        assertEquals(1, claim.conflicts().size());

        CommandRegistryTakeover.RestoreResult restored = takeover.restore(claim.takeover());

        assertTrue(restored.complete());
        assertSame(original, knownCommands.get("god"));
        verify(dispatcher).restoreRootLiteral("god", root);
    }

    @Test
    void acceptsBukkitBindingRemovedAsSideEffectOfBrigadierTakeover() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        CommandNode<CommandSourceStack> root = root("god");
        knownCommands.put("god", original);
        knownCommands.put("worldguard:god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        when(dispatcher.getRootLiteral("god")).thenReturn(root);
        when(dispatcher.takeRootLiteral("god")).thenAnswer(invocation -> {
            knownCommands.remove("god");
            return root;
        });
        when(dispatcher.restoreRootLiteral("god", root)).thenReturn(true);

        CommandRegistryTakeover takeover = new CommandRegistryTakeover(commandMap, dispatcher);
        CommandRegistryTakeover.Claim claim = takeover.claim(List.of("god"), true);

        assertTrue(claim.claimed());
        assertNull(knownCommands.get("god"));
        assertSame(original, knownCommands.get("worldguard:god"));

        CommandRegistryTakeover.RestoreResult restored = takeover.restore(claim.takeover());

        assertTrue(restored.complete());
        assertSame(original, knownCommands.get("god"));
        verify(dispatcher).restoreRootLiteral("god", root);
    }

    @Test
    void rollsBackBukkitRemovalWhenBrigadierTakeoverFails() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        CommandNode<CommandSourceStack> root = root("god");
        knownCommands.put("god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        when(dispatcher.getRootLiteral("god")).thenReturn(root);
        when(dispatcher.takeRootLiteral("god")).thenThrow(new IllegalStateException("dispatcher locked"));

        CommandRegistryTakeover takeover = new CommandRegistryTakeover(commandMap, dispatcher);

        assertThrows(IllegalStateException.class, () -> takeover.claim(List.of("god"), true));
        assertSame(original, knownCommands.get("god"));
    }

    @Test
    void doesNotOverwriteAReplacementThatAppearedBeforeRestore() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        Command replacement = new TestCommand("god");
        knownCommands.put("god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        when(dispatcher.getRootLiteral("god")).thenReturn(null);

        CommandRegistryTakeover takeover = new CommandRegistryTakeover(commandMap, dispatcher);
        CommandRegistryTakeover.Claim claim = takeover.claim(List.of("god"), true);
        knownCommands.put("god", replacement);

        CommandRegistryTakeover.RestoreResult restored = takeover.restore(claim.takeover());

        assertFalse(restored.complete());
        assertEquals(Set.of("god"), restored.skippedBukkitLabels());
        assertSame(replacement, knownCommands.get("god"));
    }

    private static CommandNode<CommandSourceStack> root(String label) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(label).build();
    }

    private static final class TestCommand extends Command {

        private TestCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return true;
        }
    }
}
