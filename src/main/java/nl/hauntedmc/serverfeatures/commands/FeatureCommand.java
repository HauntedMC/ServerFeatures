package nl.hauntedmc.serverfeatures.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class FeatureCommand extends Command {

    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    protected FeatureCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        super(name);
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        return executor.onCommand(sender, this, label, args);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (tabCompleter != null) {
            return Objects.requireNonNull(tabCompleter.onTabComplete(sender, this, alias, args));
        }
        return Collections.emptyList();
    }
}
