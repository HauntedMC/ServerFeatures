package nl.hauntedmc.serverfeatures.api.command.tab;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** Context passed to providers and predicates. */
public final class TabContext {
    private final CommandSender sender;
    private final String alias;
    private final String[] args;

    public TabContext(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        this.sender = sender;
        this.alias = alias;
        this.args = args;
    }

    public @NotNull CommandSender sender() { return sender; }
    public @NotNull String alias() { return alias; }
    public @NotNull String[] args() { return args; }
    public int size() { return args.length; }
    public String lastTokenOrEmpty() { return size() == 0 ? "" : args[size() - 1]; }

    public Optional<Player> asPlayer() {
        return (sender instanceof Player p) ? Optional.of(p) : Optional.empty();
    }
}
