package io.papermc.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class SparksFly {

    private SparksFly() {
    }

    public static final class CommandImpl extends Command {

        public CommandImpl() {
            super("spark");
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
}
