package nl.hauntedmc.serverfeatures.api.command.tab;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Lightweight wrapper to expose a stable 'complete' API from a built tree. */
public final class TabCompletion {
    private final TabTree tree;

    private TabCompletion(TabTree tree) { this.tree = tree; }

    public static TabCompletion of(TabTree tree) { return new TabCompletion(tree); }

    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return tree.complete(sender, alias, args);
    }

    public TabTree rawTree() { return tree; }
}
