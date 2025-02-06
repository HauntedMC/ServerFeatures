package nl.hauntedmc.serverfeatures.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class FeatureCommand extends Command {

    private final CommandExecutor executor;

    protected FeatureCommand(String name, CommandExecutor executor) {
        super(name);
        this.executor = executor;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        return executor.onCommand(sender, this, label, args);
    }
}
