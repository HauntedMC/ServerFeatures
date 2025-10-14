package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.brigadier.FeatureBrigadierCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FeatureCommandManager {
    private final ServerFeatures plugin;
    private final CommandMap commandMap;
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();
    private final Map<String, FeatureBrigadierCommand> registeredBrigadierCommands = new ConcurrentHashMap<>();

    public FeatureCommandManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /**
     * Registers a command dynamically at runtime and auto-wires its tab tree (if provided).
     */
    public void registerFeatureCommand(FeatureCommand command) {
        String commandName = command.getName();
        if (commandMap == null) {
            plugin.getLogger().severe("CommandMap is not initialized. Cannot register command: " + commandName);
            return;
        }
        if (registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warning("Command " + commandName + " is already registered.");
            return;
        }

        commandMap.register(plugin.getName(), command);
        registeredCommands.put(commandName, command);
        plugin.getLogger().info("Registered command: " + commandName);

        syncCommands();
    }

    /**
     * Unregisters a command dynamically (including its tabs) and
     * fully removes every mapping from the CommandMap (primary, namespaced, aliases).
     * Also resyncs Brigadier so clients lose the literal in /<tab>.
     */
    public void unregisterFeatureCommand(String commandName) {
        Logger log = plugin.getLogger();

        FeatureCommand command = registeredCommands.remove(commandName);
        if (command == null) {
            log.warning("Command " + commandName + " is not registered.");
            return;
        }

        // Unregister from the CommandMap API
        try {
            command.unregister(commandMap);
        } catch (Throwable t) {
            log.warning("CommandMap#unregister failed for " + commandName + ": " + t.getMessage());
        }

        // Aggressively purge from knownCommands: primary, namespaced, aliases
        try {
            Map<String, Command> known = commandMap.getKnownCommands();
            final String nsPrefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
            final String primary = command.getName().toLowerCase(Locale.ROOT);

            // collect keys to remove
            List<String> keys = new ArrayList<>();
            keys.add(primary);
            keys.add(nsPrefix + primary);

            List<String> aliases = command.getAliases();
            for (String a : aliases) {
                if (a == null || a.isBlank()) continue;
                final String al = a.toLowerCase(Locale.ROOT);
                keys.add(al);
                keys.add(nsPrefix + al);
            }

            // Remove exact matches that map to this command
            for (String k : keys) {
                Command mapped = known.get(k);
                if (mapped == command) {
                    known.remove(k);
                }
            }

            // Safety net: remove any remaining entries that still reference this Command instance
            known.entrySet().removeIf(e -> e.getValue() == command);

            log.info("Unregistered command: " + commandName);
        } catch (Throwable t) {
            log.warning("Failed to fully purge '" + commandName + "' from knownCommands: " + t.getMessage());
        }
    }

    /**
     * Unregisters all dynamically registered commands safely.
     */
    public void unregisterAllFeatureCommands() {
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        for (String n : names) unregisterFeatureCommand(n);
        syncCommands();
    }



    // Brigadier start
    /** Register a Brigadier command at runtime (central attach + push). */
    public void registerBrigadierFeatureCommand(FeatureBrigadierCommand command) {
        String key = command.name().toLowerCase(Locale.ROOT);

        if (registeredBrigadierCommands.putIfAbsent(key, command) != null) {
            plugin.getLogger().warning("[Brigadier] Already registered: " + key);
            return;
        }

        plugin.getBrigadierDispatcher().attachBrigadierCommand(command);

        syncCommands();
    }

    /** HARD-unregister at runtime (central detach + push). */
    public void unregisterBrigadierFeatureCommand(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        FeatureBrigadierCommand removed = registeredBrigadierCommands.remove(key);

        if (removed == null) {
            plugin.getLogger().warning("[Brigadier] Not registered: " + name);
            return;
        }

        plugin.getBrigadierDispatcher().detachBrigadierCommand(removed);

        syncCommands();
    }

    /** HARD-unregister all owned Brig commands. */
    public void unregisterAllBrigadierCommands() {
        if (registeredBrigadierCommands.isEmpty()) return;
        Collection<FeatureBrigadierCommand> snapshot = new ArrayList<>(registeredBrigadierCommands.values());
        registeredBrigadierCommands.clear();
        snapshot.forEach(cmd -> plugin.getBrigadierDispatcher().detachBrigadierCommand(cmd));
        syncCommands();
    }

    /** Total count across Bukkit + Brigadier. */
    public int getTotalRegisteredCommandCount() {
        return registeredCommands.size() + registeredBrigadierCommands.size();
    }

    /**
     * Combined, case-insensitive set of all primary command names across Bukkit + Brigadier.
     * (Returns an unmodifiable set.)
     */
    public Set<String> getAllRegisteredCommandNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.addAll(registeredCommands.keySet());
        names.addAll(registeredBrigadierCommands.keySet());
        return Collections.unmodifiableSet(names);
    }

    public Map<String, FeatureCommand> getRegisteredFeatureCommands() {
        return Collections.unmodifiableMap(registeredCommands);
    }

    public int getRegisteredFeatureCommandCount() {
        return registeredCommands.size();
    }

    public Map<String, FeatureBrigadierCommand> getRegisteredBrigadierCommands() {
        return Collections.unmodifiableMap(registeredBrigadierCommands);
    }

    public int getRegisteredBrigadierCommandCount() {
        return registeredBrigadierCommands.size();
    }

    /** Sync commands and update players. */
    private void syncCommands() {
        // Prefer CraftServer/Paper public syncCommands() if present
        try {
            Method m = plugin.getServer().getClass().getMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(plugin.getServer());
        } catch (NoSuchMethodException ignored) {
            // Older API or other impl — fall back to per-player update
        } catch (Throwable t) {
            plugin.getLogger().warning("syncCommands() failed: " + t.getMessage());
        }

        // Push new available-commands to online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable t) {
                // Extremely defensive: one player's failure shouldn't block others
                plugin.getLogger().warning("Failed to update commands for " + p.getName() + ": " + t.getMessage());
            }
        }
    }
}
