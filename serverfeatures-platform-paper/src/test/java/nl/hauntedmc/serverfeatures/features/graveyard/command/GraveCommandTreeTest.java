package nl.hauntedmc.serverfeatures.features.graveyard.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GraveCommandTreeTest {
    @Test
    void exposesOnlyNativeStringArguments() throws Exception {
        CommandNode<CommandSourceStack> root = new GraveCommand(
                mock(Graveyard.class),
                mock(GraveManager.class)
        ).buildTree();
        List<ArgumentCommandNode<?, ?>> arguments = new ArrayList<>();
        collectArguments(root, arguments);

        assertTrue(arguments.stream().allMatch(node -> node.getType() instanceof StringArgumentType));

        ArgumentCommandNode<?, ?> graveId = arguments.stream()
                .filter(node -> node.getName().equals("grave_id"))
                .findFirst()
                .orElseThrow();
        StringArgumentType type = assertInstanceOf(StringArgumentType.class, graveId.getType());
        assertEquals("RemyMine-10:27:15", type.parse(new StringReader("RemyMine-10:27:15")));
    }

    @Test
    void keepsPurgeConfirmationBeforeGreedyIdentifier() {
        CommandNode<CommandSourceStack> root = new GraveCommand(
                mock(Graveyard.class),
                mock(GraveManager.class)
        ).buildTree();

        CommandNode<CommandSourceStack> admin = assertNotNull(root.getChild("admin"));
        CommandNode<CommandSourceStack> purge = assertNotNull(admin.getChild("purge"));
        CommandNode<CommandSourceStack> confirm = assertNotNull(purge.getChild("confirm"));
        assertNotNull(confirm.getChild("grave_id"));
    }

    private static void collectArguments(
            CommandNode<?> node,
            List<ArgumentCommandNode<?, ?>> arguments
    ) {
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            arguments.add(argument);
        }
        node.getChildren().forEach(child -> collectArguments(child, arguments));
    }
}
