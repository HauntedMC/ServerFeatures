package nl.hauntedmc.serverfeatures.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class FeatureCommand extends Command {

    protected FeatureCommand(String name) {
        super(name);
    }

    public abstract boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args);
    public abstract @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args);
}
