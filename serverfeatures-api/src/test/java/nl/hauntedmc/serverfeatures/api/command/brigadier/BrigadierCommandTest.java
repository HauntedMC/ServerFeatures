package nl.hauntedmc.serverfeatures.api.command.brigadier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrigadierCommandTest {

    @Test
    void defaultAliasesAndDescriptionAreOptional() {
        BrigadierCommand command = new BrigadierCommand() {
            @Override
            public String name() {
                return "root";
            }

            @Override
            public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildTree() {
                return LiteralArgumentBuilder.<CommandSourceStack>literal(name()).build();
            }
        };

        assertEquals("root", command.name());
        assertEquals("root", command.buildTree().getLiteral());
        assertTrue(command.aliases().isEmpty());
        assertNull(command.description());
    }

    @Test
    void customAliasesAndDescriptionCanBeProvided() {
        BrigadierCommand command = new BrigadierCommand() {
            @Override
            public String name() {
                return "root";
            }

            @Override
            public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildTree() {
                return LiteralArgumentBuilder.<CommandSourceStack>literal(name()).build();
            }

            @Override
            public List<String> aliases() {
                return List.of("r", "rt");
            }

            @Override
            public String description() {
                return "test";
            }
        };

        assertEquals(List.of("r", "rt"), command.aliases());
        assertEquals("test", command.description());
    }
}
