package nl.hauntedmc.serverfeatures.api.command.brigadier;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Root-literal Brigadier command contributed by a feature.
 */
public interface BrigadierCommand {
    /**
     * Root literal name, e.g. "abrig" (avoid collisions with existing Bukkit commands while testing).
     */
    @NotNull String name();

    /**
     * Build the full Brigadier tree for this root literal.
     */
    @NotNull LiteralCommandNode<CommandSourceStack> buildTree();

    /**
     * Optional aliases for the root literal.
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Optional description shown in client help/registrar.
     */
    default String description() {
        return null;
    }
}
