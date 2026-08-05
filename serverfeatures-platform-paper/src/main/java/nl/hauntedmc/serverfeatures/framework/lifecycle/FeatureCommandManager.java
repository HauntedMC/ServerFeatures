package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.util.text.TextPatterns;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers feature commands while coordinating plugin-wide label ownership and reversible external takeovers.
 */
public class FeatureCommandManager {

    private final ServerFeatures plugin;
    private final CommandMap commandMap;
    private final FeatureCommandOwnership ownership;
    private final CommandRegistryTakeover registryTakeover;
    private final Map<String, FeatureCommand> registeredCommands = new ConcurrentHashMap<>();
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredCommandLabels = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredBrigadierLabels = new ConcurrentHashMap<>();
    private final Map<String, CommandRegistryTakeover.Takeover> bukkitTakeovers = new ConcurrentHashMap<>();
    private final Map<String, CommandRegistryTakeover.Takeover> brigadierTakeovers = new ConcurrentHashMap<>();

    public FeatureCommandManager(ServerFeatures plugin) {
        this(plugin, new FeatureCommandOwnership());
    }

    FeatureCommandManager(ServerFeatures plugin, FeatureCommandOwnership ownership) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
        this.ownership = ownership;
        this.registryTakeover = new CommandRegistryTakeover(commandMap, plugin.getBrigadierDispatcher());
    }

    public void registerFeatureCommand(@NotNull FeatureCommand command) {
        runOnMain(() -> doRegisterBukkit(command));
    }

    private void doRegisterBukkit(FeatureCommand command) {
        String name = normalize(command.getName());
        if (registeredCommands.containsKey(name)) {
            plugin.getLogger().warning("Command " + name + " is already registered by this feature.");
            return;
        }
        List<String> labels = commandLabels(name, command.getAliases());
        String collision = ownership.claim(command, labels);
        if (collision != null) {
            plugin.getLogger().warning("Command label '" + collision
                    + "' is already owned by another ServerFeatures feature; skipping command '" + name + "'.");
            return;
        }

        CommandRegistryTakeover.Claim claim;
        try {
            claim = registryTakeover.claim(labels, plugin.getConfigHandler().shouldOverwriteCommandConflicts());
        } catch (Throwable throwable) {
            ownership.release(command, labels);
            plugin.getLogger().warning("Failed to prepare command labels for '" + name
                    + "': " + throwable.getMessage());
            return;
        }
        if (!claim.claimed()) {
            ownership.release(command, labels);
            logBlockedConflict(name, claim.blockingConflict(), false);
            return;
        }
        logTakeovers(claim.conflicts(), name, false);

        boolean registered = false;
        try {
            boolean primary = commandMap.register(plugin.getName(), command);
            if (!primary || commandMap.getCommand(name) != command) {
                purgeBukkitCommand(command, labels);
                plugin.getLogger().warning("Command '" + name
                        + "' could not claim its primary label and was not registered.");
                return;
            }
            registeredCommands.put(name, command);
            registeredCommandLabels.put(name, labels);
            bukkitTakeovers.put(name, claim.takeover());
            registered = true;
            plugin.getLogger().info("Registered command: " + name);
        } catch (Throwable throwable) {
            purgeBukkitCommand(command, labels);
            plugin.getLogger().warning("Failed to register Bukkit command '" + name
                    + "': " + throwable.getMessage());
        } finally {
            if (!registered) {
                restoreTakeover(claim.takeover(), name);
                ownership.release(command, labels);
            }
        }
    }

    public void unregisterFeatureCommand(@NotNull String commandName) {
        runOnMain(() -> doUnregisterBukkit(commandName));
    }

    public void unregisterAllFeatureCommands() {
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        runOnMain(() -> names.forEach(this::doUnregisterBukkit));
    }

    private void doUnregisterBukkit(String commandName) {
        String name = normalize(commandName);
        FeatureCommand command = registeredCommands.remove(name);
        if (command == null) {
            plugin.getLogger().warning("Command " + name + " is not registered.");
            return;
        }
        List<String> labels = registeredCommandLabels.remove(name);
        if (labels == null) {
            labels = commandLabels(name, command.getAliases());
        }
        try {
            command.unregister(commandMap);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("CommandMap#unregister failed for " + name
                    + ": " + throwable.getMessage());
        } finally {
            purgeBukkitCommand(command, labels);
            ownership.release(command, labels);
            restoreTakeover(bukkitTakeovers.remove(name), name);
        }
        plugin.getLogger().info("Unregistered command: " + name);
    }

    private void purgeBukkitCommand(FeatureCommand command, List<String> labels) {
        try {
            Map<String, Command> known = commandMap.getKnownCommands();
            CommandRegistryKeys.purgeKnownCommands(
                    known,
                    command,
                    CommandRegistryKeys.knownCommandKeys(plugin.getName(), command.getName(), labels)
            );
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to purge Bukkit command '" + command.getName()
                    + "': " + throwable.getMessage());
        }
    }

    public void registerBrigadierCommand(@NotNull BrigadierCommand command) {
        runOnMain(() -> doRegisterBrigadier(command));
    }

    private void doRegisterBrigadier(BrigadierCommand command) {
        String name = validateLabel(command.name(), "command name");
        if (registeredBrigadierCommands.containsKey(name)) {
            plugin.getLogger().warning("[Brigadier] Already registered by this feature: " + name);
            return;
        }
        List<String> aliases = sanitizeAliases(command.aliases(), name);
        List<String> labels = commandLabels(name, aliases);
        String collision = ownership.claim(command, labels);
        if (collision != null) {
            plugin.getLogger().warning("[Brigadier] Root label '" + collision
                    + "' is already owned by another ServerFeatures feature; skipping /" + name + ".");
            return;
        }

        CommandRegistryTakeover.Claim claim;
        try {
            claim = registryTakeover.claim(labels, plugin.getConfigHandler().shouldOverwriteCommandConflicts());
        } catch (Throwable throwable) {
            ownership.release(command, labels);
            plugin.getLogger().warning("[Brigadier] Failed to prepare labels for /" + name
                    + ": " + throwable.getMessage());
            return;
        }
        if (!claim.claimed()) {
            ownership.release(command, labels);
            logBlockedConflict(name, claim.blockingConflict(), true);
            return;
        }
        logTakeovers(claim.conflicts(), name, true);

        boolean registered = false;
        try {
            if (!plugin.getBrigadierDispatcher().attachBrigadierCommand(command, name, aliases)) {
                plugin.getLogger().warning("[Brigadier] Could not attach /" + name
                        + " after its labels were prepared.");
                return;
            }
            registeredBrigadierCommands.put(name, command);
            registeredBrigadierLabels.put(name, labels);
            brigadierTakeovers.put(name, claim.takeover());
            registered = true;
            plugin.getLogger().info("[Brigadier] Registered /" + name
                    + " (" + aliases.size() + " aliases)");
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Brigadier] Attach failed for /" + name
                    + ": " + throwable.getMessage());
        } finally {
            if (!registered) {
                restoreTakeover(claim.takeover(), name);
                ownership.release(command, labels);
            }
        }
    }

    public void unregisterBrigadierCommand(@NotNull String commandName) {
        runOnMain(() -> doUnregisterBrigadier(commandName));
    }

    private void doUnregisterBrigadier(String commandName) {
        String name = normalize(commandName);
        BrigadierCommand command = registeredBrigadierCommands.remove(name);
        if (command == null) {
            plugin.getLogger().warning("[Brigadier] Not registered: " + name);
            return;
        }
        List<String> labels = registeredBrigadierLabels.remove(name);
        if (labels == null) {
            labels = commandLabels(name, sanitizeAliases(command.aliases(), name));
        }
        try {
            plugin.getBrigadierDispatcher().detachBrigadierCommand(command, labels);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Brigadier] Detach failed for /" + name
                    + ": " + throwable.getMessage());
        } finally {
            ownership.release(command, labels);
            restoreTakeover(brigadierTakeovers.remove(name), name);
        }
    }

    public void unregisterAllBrigadierCommands() {
        List<String> names = new ArrayList<>(registeredBrigadierCommands.keySet());
        runOnMain(() -> names.forEach(this::doUnregisterBrigadier));
    }

    public int getTotalRegisteredCommandCount() {
        return registeredCommands.size() + registeredBrigadierCommands.size();
    }

    public Set<String> getAllRegisteredCommandNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>(registeredCommands.keySet());
        names.addAll(registeredBrigadierCommands.keySet());
        return Collections.unmodifiableSet(names);
    }

    public Map<String, FeatureCommand> getRegisteredFeatureCommands() {
        return Map.copyOf(registeredCommands);
    }

    public Map<String, FeatureCommand> getRegisteredCommands() {
        return getRegisteredFeatureCommands();
    }

    public int getRegisteredFeatureCommandCount() {
        return registeredCommands.size();
    }

    public int getRegisteredCommandCount() {
        return getRegisteredFeatureCommandCount();
    }

    public Map<String, BrigadierCommand> getRegisteredBrigadierCommands() {
        return Map.copyOf(registeredBrigadierCommands);
    }

    public int getRegisteredBrigadierCommandCount() {
        return registeredBrigadierCommands.size();
    }

    private void logBlockedConflict(
            String commandName,
            CommandRegistryTakeover.Conflict conflict,
            boolean brigadier
    ) {
        String prefix = brigadier ? "[Brigadier] " : "";
        plugin.getLogger().warning(prefix + "Command label '/" + conflict.label() + "' is already owned by "
                + conflict.ownerDescription() + "; skipping /" + commandName
                + " because global.commands.overwrite-conflicts is false.");
    }

    private void logTakeovers(
            List<CommandRegistryTakeover.Conflict> conflicts,
            String commandName,
            boolean brigadier
    ) {
        String prefix = brigadier ? "[Brigadier] " : "";
        for (CommandRegistryTakeover.Conflict conflict : conflicts) {
            plugin.getLogger().warning(prefix + "Replacing command label '/" + conflict.label() + "' from "
                    + conflict.ownerDescription() + " while registering /" + commandName + ".");
        }
    }

    private void restoreTakeover(CommandRegistryTakeover.Takeover takeover, String commandName) {
        if (takeover == null || takeover.isEmpty()) {
            return;
        }
        try {
            CommandRegistryTakeover.RestoreResult result = registryTakeover.restore(takeover);
            if (!result.skippedBukkitLabels().isEmpty()) {
                plugin.getLogger().warning("Could not restore displaced Bukkit labels after unregistering /"
                        + commandName + ": " + String.join(", ", result.skippedBukkitLabels()));
            }
            if (!result.skippedBrigadierLabels().isEmpty()) {
                plugin.getLogger().warning("Could not restore displaced Brigadier roots after unregistering /"
                        + commandName + ": " + String.join(", ", result.skippedBrigadierLabels()));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to restore displaced command labels after unregistering /"
                    + commandName + ": " + throwable.getMessage());
        }
    }

    private static List<String> commandLabels(String name, Collection<String> aliases) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        labels.add(normalize(name));
        for (String alias : aliases) {
            labels.add(normalize(alias));
        }
        return List.copyOf(labels);
    }

    private static List<String> sanitizeAliases(Collection<String> aliases, String commandName) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String normalized = validateLabel(alias, "command alias");
            if (!normalized.equals(commandName)) {
                sanitized.putIfAbsent(normalized, normalized);
            }
        }
        return List.copyOf(sanitized.values());
    }

    private static String validateLabel(String label, String description) {
        String normalized = normalize(label);
        if (!TextPatterns.BUKKIT_ALIAS_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + description + ": " + label);
        }
        return normalized;
    }

    private static String normalize(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }

    private void runOnMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }
}
