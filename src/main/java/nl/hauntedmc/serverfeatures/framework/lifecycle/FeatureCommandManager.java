package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class FeatureCommandManager {
    private final ServerFeatures plugin;
    private final CommandMap commandMap;

    // Concurrent maps so reads are safe off-thread; mutations are still marshalled to main.
    private final Map<String, FeatureCommand> registeredCommands = new ConcurrentHashMap<>();
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new ConcurrentHashMap<>();

    // Debounce sync when triggered off-thread
    private final AtomicBoolean syncQueued = new AtomicBoolean(false);

    public FeatureCommandManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /* ========================== Bukkit / Legacy ========================== */

    /**
     * Registers a Bukkit command at runtime (thread-safe).
     */
    public void registerFeatureCommand(FeatureCommand command) {
        final String name = command.getName();
        // Atomic dup check first to avoid scheduling useless work
        if (registeredCommands.putIfAbsent(name, command) != null) {
            plugin.getLogger().warning("Command " + name + " is already registered.");
            return;
        }
        runOnMain(() -> {
            if (commandMap == null) {
                plugin.getLogger().severe("CommandMap is not initialized. Cannot register command: " + name);
                registeredCommands.remove(name, command);
                return;
            }
            // Register with Bukkit
            commandMap.register(plugin.getName(), command);
            plugin.getLogger().info("Registered command: " + name);
            syncCommands();
        });
    }

    /**
     * Unregisters a Bukkit command at runtime (thread-safe).
     */
    public void unregisterFeatureCommand(String commandName) {
        runOnMain(() -> {
            doUnregisterBukkit(commandName); // no sync inside
            syncCommands();                  // single sync for the single op
        });
    }

    /**
     * Unregisters all Bukkit commands owned by this feature (thread-safe).
     */
    public void unregisterAllFeatureCommands() {
        // snapshot off-thread is fine
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        runOnMain(() -> {
            for (String n : names) {
                doUnregisterBukkit(n);       // no sync inside
            }
            syncCommands();                  // one sync after the batch
        });
    }

    /**
     * Unregisters a Bukkit command at runtime (thread-safe).
     */
    private void doUnregisterBukkit(String commandName) {
        final Logger log = plugin.getLogger();

        // Remove from our registry first; idempotent if already removed
        FeatureCommand cmd = registeredCommands.remove(commandName);
        if (cmd == null) {
            log.warning("Command " + commandName + " is not registered.");
            return;
        }

        // Unregister from the CommandMap
        try {
            cmd.unregister(commandMap);
        } catch (Throwable t) {
            log.warning("CommandMap#unregister failed for " + commandName + ": " + t.getMessage());
        }

        // Hard purge from knownCommands (primary + namespaced + aliases)
        try {
            Map<String, Command> known = getKnownCommands(cmd);
            known.entrySet().removeIf(e -> e.getValue() == cmd);

            log.info("Unregistered command: " + commandName);
        } catch (Throwable t) {
            log.warning("Failed to fully purge '" + commandName + "' from knownCommands: " + t.getMessage());
        }
    }

    private @NotNull Map<String, Command> getKnownCommands(FeatureCommand cmd) {
        Map<String, Command> known = commandMap.getKnownCommands();
        final String nsPrefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
        final String primary = cmd.getName().toLowerCase(Locale.ROOT);

        List<String> keys = new ArrayList<>();
        keys.add(primary);
        keys.add(nsPrefix + primary);

        List<String> aliases = cmd.getAliases();
        for (String a : aliases) {
            if (a == null || a.isBlank()) continue;
            final String al = a.toLowerCase(Locale.ROOT);
            keys.add(al);
            keys.add(nsPrefix + al);
        }

        for (String k : keys) {
            Command mapped = known.get(k);
            if (mapped == cmd) known.remove(k);
        }
        return known;
    }

    /* ============================= Brigadier ============================= */

    /**
     * Register a Brigadier root command at runtime (thread-safe).
     */
    public void registerBrigadierCommand(BrigadierCommand command) {
        final String key = command.name().toLowerCase(Locale.ROOT);
        if (registeredBrigadierCommands.putIfAbsent(key, command) != null) {
            plugin.getLogger().warning("[Brigadier] Already registered: " + key);
            return;
        }
        runOnMain(() -> {
            plugin.getBrigadierDispatcher().attachBrigadierCommand(command);
            syncCommands();
        });
    }


    /**
     * HARD-unregister all Brigadier root commands owned by this feature (thread-safe).
     */
    public void unregisterAllBrigadierCommands() {
        if (registeredBrigadierCommands.isEmpty()) return;
        Collection<BrigadierCommand> snapshot = new ArrayList<>(registeredBrigadierCommands.values());
        registeredBrigadierCommands.clear();
        runOnMain(() -> {
            snapshot.forEach(cmd -> plugin.getBrigadierDispatcher().detachBrigadierCommand(cmd));
            syncCommands();
        });
    }

    /* ========================== Combined helpers ========================= */

    /**
     * Total count across Bukkit + Brigadier.
     */
    public int getTotalRegisteredCommandCount() {
        return registeredCommands.size() + registeredBrigadierCommands.size();
    }

    /**
     * Combined, case-insensitive set of all primary command names (snapshot, unmodifiable).
     */
    public Set<String> getAllRegisteredCommandNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.addAll(registeredCommands.keySet());
        names.addAll(registeredBrigadierCommands.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * Safe snapshots (unmodifiable) for off-thread reads.
     */
    public Map<String, FeatureCommand> getRegisteredFeatureCommands() {
        return Map.copyOf(registeredCommands);
    }

    public int getRegisteredFeatureCommandCount() {
        return registeredCommands.size();
    }

    public Map<String, BrigadierCommand> getRegisteredBrigadierCommands() {
        return Map.copyOf(registeredBrigadierCommands);
    }

    public int getRegisteredBrigadierCommandCount() {
        return registeredBrigadierCommands.size();
    }

    /* ============================ Sync helpers =========================== */

    /**
     * Ensure code runs on the primary server thread.
     */
    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /**
     * Sync Bukkit/Brig trees with the client.
     * - If on main: do it now.
     * - If off-thread: debounce to one run later this tick.
     */
    private void syncCommands() {
        if (Bukkit.isPrimaryThread()) {
            doSyncCommands();
            return;
        }
        // Debounce off-thread calls
        if (syncQueued.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    doSyncCommands();
                } finally {
                    syncQueued.set(false);
                }
            });
        }
    }

    /**
     * Actual sync work (must be called on main).
     */
    private void doSyncCommands() {
        try {
            Method m = plugin.getServer().getClass().getMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(plugin.getServer());
        } catch (NoSuchMethodException ignored) {
            // Older impls; it's OK to only push to players.
        } catch (Throwable t) {
            plugin.getLogger().warning("syncCommands() failed: " + t.getMessage());
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to update commands for " + p.getName() + ": " + t.getMessage());
            }
        }
    }
}
