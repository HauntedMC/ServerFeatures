package nl.hauntedmc.serverfeatures.api.command;

import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureCommandTest {

    @Test
    void constructorSanitizesAliasesAndAppliesPermission() {
        TestCommand cmd = new TestCommand(new CommandMeta.Builder("root")
                .aliases(List.of("ROOT", "Alias", "alias", "ok_name-1"))
                .permission("serverfeatures.use")
                .build());

        assertEquals(List.of("alias", "ok_name-1"), cmd.getAliases());
        assertEquals("serverfeatures.use", cmd.getPermission());
    }

    @Test
    void constructorRejectsInvalidAliases() {
        assertThrows(IllegalArgumentException.class, () -> new TestCommand(new CommandMeta.Builder("root")
                .aliases(List.of("bad alias"))
                .build()));

        assertThrows(IllegalArgumentException.class, () -> new TestCommand(new CommandMeta.Builder("root")
                .aliases(List.of("bad!"))
                .build()));
    }

    private static final class TestCommand extends FeatureCommand {
        private TestCommand(@NotNull CommandMeta spec) {
            super(spec);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
            return true;
        }
    }
}
