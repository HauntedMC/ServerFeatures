package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FeatureCommandManager {
    private final ServerFeatures plugin;
    private final CommandMap commandMap;

    // Concurrent for safe off-thread reads & dup checks (mutations still marshalled to main)
    private final Map<String, FeatureCommand> registeredCommands = new ConcurrentHashMap<>();
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new ConcurrentHashMap<>();

    public FeatureCommandManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /* ========================== Bukkit / Legacy ========================== */

    /**
     * Registers a Bukkit command at runtime (thread-safe).
     */
    public void registerFeatureCommand(@NotNull FeatureCommand command) {
        final String name = command.getName();
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
            try {
                commandMap.register(plugin.getName(), command);
                plugin.getLogger().info("Registered command: " + name);
            } catch (Throwable t) {
                // Roll back our registry entry if the actual register fails
                registeredCommands.remove(name, command);
                plugin.getLogger().warning("Failed to register Bukkit command '" + name + "': " + t.getMessage());
            }
        });
    }

    /**
     * Unregisters a single Bukkit command (thread-safe).
     */
    public void unregisterFeatureCommand(@NotNull String commandName) {
        runOnMain(() -> doUnregisterBukkit(commandName));
    }

    /**
     * Unregisters all Bukkit commands owned by this feature (thread-safe).
     */
    public void unregisterAllFeatureCommands() {
        // Snapshot is safe off-thread due to CHM
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        runOnMain(() -> names.forEach(this::doUnregisterBukkit));
    }

    /**
     * Main-thread only: hard-remove from CommandMap + knownCommands.
     */
    private void doUnregisterBukkit(@NotNull String commandName) {
        final Logger log = plugin.getLogger();
        final FeatureCommand cmd = registeredCommands.remove(commandName);
        if (cmd == null) {
            log.warning("Command " + commandName + " is not registered.");
            return;
        }

        // Unregister from the CommandMap API
        try {
            cmd.unregister(commandMap);
        } catch (Throwable t) {
            log.warning("CommandMap#unregister failed for " + commandName + ": " + t.getMessage());
        }

        // Aggressively purge from knownCommands: primary, namespaced, aliases
        try {
            Map<String, Command> known = commandMap.getKnownCommands();
            List<String> keys = CommandRegistryKeys.knownCommandKeys(plugin.getName(), cmd.getName(), cmd.getAliases());
            CommandRegistryKeys.purgeKnownCommands(known, cmd, keys);

            log.info("Unregistered command: " + commandName);
        } catch (Throwable t) {
            log.warning("Failed to fully purge '" + commandName + "' from knownCommands: " + t.getMessage());
        }
    }

    /* ============================= Brigadier ============================= */

    /**
     * Register a Brigadier root command at runtime (thread-safe).
     */
    public void registerBrigadierCommand(@NotNull BrigadierCommand command) {
        final String key = command.name().toLowerCase(Locale.ROOT);
        if (registeredBrigadierCommands.putIfAbsent(key, command) != null) {
            plugin.getLogger().warning("[Brigadier] Already registered: " + key);
            return;
        }
        // All dispatcher mutations must happen on the main thread
        runOnMain(() -> {
            try {
                plugin.getBrigadierDispatcher().attachBrigadierCommand(command);
            } catch (Throwable t) {
                // Roll back our registry entry if attach fails
                registeredBrigadierCommands.remove(key, command);
                plugin.getLogger().warning("[Brigadier] attach failed for /" + key + ": " + t.getMessage());
            }
        });
    }

    /**
     * Unregister a single Brigadier root command (thread-safe).
     */
    public void unregisterBrigadierCommand(@NotNull String name) {
        final String key = name.toLowerCase(Locale.ROOT);
        final BrigadierCommand removed = registeredBrigadierCommands.remove(key);
        if (removed == null) {
            plugin.getLogger().warning("[Brigadier] Not registered: " + name);
            return;
        }
        runOnMain(() -> {
            try {
                plugin.getBrigadierDispatcher().detachBrigadierCommand(removed);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Brigadier] detach failed for /" + key + ": " + t.getMessage());
            }
        });
    }

    /**
     * HARD-unregister all Brigadier root commands owned by this feature (thread-safe).
     */
    public void unregisterAllBrigadierCommands() {
        if (registeredBrigadierCommands.isEmpty()) return;
        // Snapshot + clear first so concurrent readers get a consistent view
        Collection<BrigadierCommand> snapshot = new ArrayList<>(registeredBrigadierCommands.values());
        registeredBrigadierCommands.clear();
        runOnMain(() -> snapshot.forEach(cmd -> {
            try {
                plugin.getBrigadierDispatcher().detachBrigadierCommand(cmd);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Brigadier] detach failed for /" + cmd.name() + ": " + t.getMessage());
            }
        }));
    }

    /* ========================== Combined helpers ========================= */

    public int getTotalRegisteredCommandCount() {
        return registeredCommands.size() + registeredBrigadierCommands.size();
    }

    public Set<String> getAllRegisteredCommandNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.addAll(registeredCommands.keySet());
        names.addAll(registeredBrigadierCommands.keySet());
        return Collections.unmodifiableSet(names);
    }

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

    /* ============================ Threading ============================== */

    /**
     * Ensure code runs on the primary server thread.
     */
    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
