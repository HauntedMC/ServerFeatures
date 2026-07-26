package nl.hauntedmc.serverfeatures.framework.command.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
