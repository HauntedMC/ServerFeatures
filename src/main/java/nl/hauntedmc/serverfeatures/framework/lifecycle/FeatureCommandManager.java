package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.util.*;
import java.util.logging.Logger;

public class FeatureCommandManager {
    private final ServerFeatures plugin;
    private final CommandMap commandMap;

    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new HashMap<>();

    public FeatureCommandManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /**
     * Registers a Bukkit command at runtime (thread-safe).
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
    }

    /**
     * Unregisters all Bukkit commands owned by this feature (thread-safe).
     */
    public void unregisterAllFeatureCommands() {
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        for (String n : names) doUnregisterBukkit(n);
    }

    /**
     * Unregisters a Bukkit command at runtime (thread-safe).
     */
    private void doUnregisterBukkit(String commandName) {
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
     * Register a Brigadier root command at runtime (thread-safe).
     */
    public void registerBrigadierCommand(BrigadierCommand command) {
        final String key = command.name().toLowerCase(Locale.ROOT);
        if (registeredBrigadierCommands.putIfAbsent(key, command) != null) {
            plugin.getLogger().warning("[Brigadier] Already registered: " + key);
            return;
        }
        plugin.getBrigadierDispatcher().attachBrigadierCommand(command);
    }


    /**
     * HARD-unregister all Brigadier root commands owned by this feature (thread-safe).
     */
    public void unregisterAllBrigadierCommands() {
        if (registeredBrigadierCommands.isEmpty()) return;
        Collection<BrigadierCommand> snapshot = new ArrayList<>(registeredBrigadierCommands.values());
        registeredBrigadierCommands.clear();
        snapshot.forEach(cmd -> plugin.getBrigadierDispatcher().detachBrigadierCommand(cmd));
    }

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
}
