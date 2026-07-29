package nl.hauntedmc.serverfeatures.features.invtools.command;

import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class InvToolsCommandTest {

    @Test
    void exposesTheInventoryAndEnderChestCommandTree() {
        CommandNode<CommandSourceStack> root = new InvToolsCommand(mock(InvTools.class)).buildTree();

        assertNotNull(root.getChild("inventory"));
        assertNotNull(root.getChild("inventory").getChild("open"));
        assertNotNull(root.getChild("inventory").getChild("clear"));
        assertNotNull(root.getChild("inventory").getChild("open").getChild("player"));
        assertNotNull(root.getChild("inventory").getChild("clear").getChild("player"));
        assertNotNull(root.getChild("enderchest"));
        assertNotNull(root.getChild("enderchest").getChild("open"));
        assertNotNull(root.getChild("enderchest").getChild("clear"));
    }
}
