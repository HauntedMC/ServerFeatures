package nl.hauntedmc.serverfeatures.api.command.tab;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.*;

/**
 * Global TabService:
 * - Holds the label -> TabTree registry
 * - Provides callSync() for safe main-thread access
 * - No event handling here; the global listener will call into this service
 */
public final class TabService implements TabRequest.SyncCaller {

    private final Plugin plugin;
    private final Map<String, TabTree> byLabel = new ConcurrentHashMap<>();

    public TabService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register a tree for a label (primary command name or alias). */
    public void register(String labelLowercase, TabTree tree) {
        byLabel.put(
                Objects.requireNonNull(labelLowercase, "label").toLowerCase(Locale.ROOT),
                Objects.requireNonNull(tree, "tree")
        );
    }

    /** Unregister a previously registered label. */
    public void unregister(String labelLowercase) {
        if (labelLowercase != null) byLabel.remove(labelLowercase.toLowerCase(Locale.ROOT));
    }

    /** Lookup a tree by lowercased label (primary or alias). */
    public Optional<TabTree> find(String labelLowercase) {
        if (labelLowercase == null) return Optional.empty();
        return Optional.ofNullable(byLabel.get(labelLowercase.toLowerCase(Locale.ROOT)));
    }

    /** Execute supplier on main thread and wait for result up to timeout. */
    @Override
    public <T> T callSync(java.util.function.Supplier<T> supplier, long timeout, TimeUnit unit) throws Exception {
        Future<T> f = Bukkit.getScheduler().callSyncMethod(plugin, supplier::get);
        return f.get(Math.max(1, unit.toMillis(timeout)), TimeUnit.MILLISECONDS);
    }
}
