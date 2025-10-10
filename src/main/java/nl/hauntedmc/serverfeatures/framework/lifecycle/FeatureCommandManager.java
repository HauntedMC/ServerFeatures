package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

public class FeatureCommandManager {
    private final ServerFeatures plugin;
    private final CommandMap commandMap;
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

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

        try {
            TabTree tree = command.createTabTree();
            if (tree != null) {
                command.registerTabTree(plugin.getTabService(), tree);
                plugin.getLogger().info("Registered tab-completions for: " + commandName);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("TabTree setup failed for " + commandName + ": " + t.getMessage());
        }

        // Ensure clients see the new command immediately
        trySyncCommands();
    }

    /**
     * Unregisters a command dynamically (including its tabs) and
     * fully removes every mapping from the CommandMap (primary, namespaced, aliases).
     * Also resyncs Brigadier so clients lose the literal in /<tab>.
     */
    public void unregisterCommand(String commandName) {
        Logger log = plugin.getLogger();

        FeatureCommand command = registeredCommands.remove(commandName);
        if (command == null) {
            log.warning("Command " + commandName + " is not registered.");
            return;
        }

        // 1) Unregister tab completions first (global listener uses these)
        try {
            command.unregisterTabTree();
        } catch (Throwable t) {
            log.warning("Failed to unregister tabs for " + commandName + ": " + t.getMessage());
        }

        // 2) Unregister from the CommandMap API
        try {
            command.unregister(commandMap);
        } catch (Throwable t) {
            log.warning("CommandMap#unregister failed for " + commandName + ": " + t.getMessage());
        }

        // 3) Aggressively purge from knownCommands: primary, namespaced, aliases
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

        // 4) Resync Brigadier & push new tree to clients
        trySyncCommands();
    }

    /**
     * Unregisters all dynamically registered commands safely.
     */
    public void unregisterAllCommands() {
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        for (String n : names) unregisterCommand(n);
    }

    /**
     * Get all registered commands of the feature.
     */
    public Map<String, FeatureCommand> getRegisteredCommands() {
        return registeredCommands;
    }

    public int getRegisteredCommandCount() {
        return registeredCommands.size();
    }

    /* ==================== helpers ==================== */

    /** Try Paper's syncCommands() if available, and always update players. */
    private void trySyncCommands() {
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
