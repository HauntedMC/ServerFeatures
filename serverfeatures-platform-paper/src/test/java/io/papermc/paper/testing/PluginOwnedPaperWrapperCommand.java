package io.papermc.paper.testing;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class PluginOwnedPaperWrapperCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin plugin;

    public PluginOwnedPaperWrapperCommand(String name, Plugin plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public @NotNull Plugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String commandLabel,
            @NotNull String @NotNull [] args
    ) {
        return true;
    }
}
