package nl.hauntedmc.serverfeatures.api.command.tab;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Request context + helper om veilig op de main thread data te halen.
 * callSync(...) blokkeert de async tab thread (niet de main thread).
 */
public final class TabRequest {
    private final CommandSender sender;
    private final String alias;
    private final String[] args;
    private final MatchState state;
    private final SyncCaller sync;


    public interface SyncCaller {
        <T> T callSync(Supplier<T> supplier, long timeout, TimeUnit unit) throws Exception;
    }

    public TabRequest(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args, @NotNull MatchState state, @NotNull SyncCaller sync) {
        this.sender = sender; this.alias = alias; this.args = args; this.state = state; this.sync = sync;
    }

    public @NotNull CommandSender sender() { return sender; }
    public @NotNull String alias() { return alias; }
    public @NotNull String[] args() { return args; }
    public @NotNull MatchState state() { return state; }

    public int size() { return args.length; }
    public String last() { return size() == 0 ? "" : args[size() - 1]; }
    public Optional<Player> asPlayer() { return (sender instanceof Player p) ? Optional.of(p) : Optional.empty(); }

    /** Draai supplier op main thread; throw bij timeout/errors. */
    public <T> T callSync(Supplier<T> supplier) {
        try { return sync.callSync(supplier, 2, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception e) { throw new RuntimeException("callSync failed", e); }
    }

    /** Overload die exact matcht met SyncCaller (voor method references). */
    public <T> T callSync(Supplier<T> supplier, long timeout, TimeUnit unit) {
        try {
            return sync.callSync(supplier, timeout, unit);
        } catch (Exception e) {
            throw new RuntimeException("callSync failed", e);
        }
    }
}
