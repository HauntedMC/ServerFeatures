package nl.hauntedmc.serverfeatures.framework.command.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrigadierDispatcherTest {

    @Test
    void removeRootLiteralRemovesExistingRootCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("serverfeatures"));

        boolean removed = BrigadierDispatcher.removeRootLiteral(dispatcher, "serverfeatures");

        assertTrue(removed);
        assertNull(dispatcher.getRoot().getChild("serverfeatures"));
    }

    @Test
    void removeRootLiteralReturnsFalseForInvalidInputOrMissingLiteral() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        assertFalse(BrigadierDispatcher.removeRootLiteral(dispatcher, "missing"));
        assertFalse(BrigadierDispatcher.removeRootLiteral(dispatcher, ""));
        assertFalse(BrigadierDispatcher.removeRootLiteral(dispatcher, null));
        assertFalse(BrigadierDispatcher.removeRootLiteral(null, "x"));
    }

    @Test
    void attachesAndDetachesTheValidatedRegistrationLabels() {
        CommandDispatcher<CommandSourceStack> commandDispatcher = new CommandDispatcher<>();
        BrigadierDispatcher dispatcher = dispatcher(commandDispatcher);
        BrigadierCommand command = mock(BrigadierCommand.class);
        when(command.name()).thenReturn("RAW");
        when(command.buildTree()).thenReturn(
                LiteralArgumentBuilder.<CommandSourceStack>literal("validated").build()
        );

        assertTrue(dispatcher.attachBrigadierCommand(command, "validated", List.of("alias")));
        assertNotNull(commandDispatcher.getRoot().getChild("validated"));
        assertNotNull(commandDispatcher.getRoot().getChild("alias"));

        dispatcher.detachBrigadierCommand(command, List.of("validated", "alias"));
        assertNull(commandDispatcher.getRoot().getChild("validated"));
        assertNull(commandDispatcher.getRoot().getChild("alias"));
    }

    @Test
    void refusesTreeWhoseLiteralDoesNotMatchTheValidatedName() {
        CommandDispatcher<CommandSourceStack> commandDispatcher = new CommandDispatcher<>();
        BrigadierDispatcher dispatcher = dispatcher(commandDispatcher);
        BrigadierCommand command = mock(BrigadierCommand.class);
        when(command.buildTree()).thenReturn(
                LiteralArgumentBuilder.<CommandSourceStack>literal("different").build()
        );

        assertFalse(dispatcher.attachBrigadierCommand(command, "validated", List.of("alias")));
        assertNull(commandDispatcher.getRoot().getChild("different"));
        assertNull(commandDispatcher.getRoot().getChild("alias"));
    }

    private static BrigadierDispatcher dispatcher(CommandDispatcher<CommandSourceStack> dispatcher) {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("brigadier-dispatcher-test"));
        return new BrigadierDispatcher(plugin, dispatcher);
    }
}
