package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import nl.hauntedmc.serverfeatures.features.builtincommandblocker.BuiltinCommandBlocker;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class BuiltinCommandBlockerService {

    private static final String GENERATED_COUNT = "generated.blocked_command_count";
    private static final String GENERATED_COMMANDS = "generated.blocked_commands";
    private static final String GENERATED_SOURCES = "generated.detected_sources";

    private final BuiltinCommandBlocker feature;
    private final CommandMap commandMap;
    private final Map<String, Command> removedCommands = new LinkedHashMap<>();
    private volatile BuiltinCommandSnapshot snapshot = BuiltinCommandSnapshot.EMPTY;

    public BuiltinCommandBlockerService(BuiltinCommandBlocker feature, CommandMap commandMap) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.commandMap = Objects.requireNonNull(commandMap, "commandMap");
    }

    /**
     * Rebuilds the immutable command snapshot from Paper's current command map and reconciles hard removal.
     *
     * @return whether the effective blocked-command set or command-map state changed
     */
    public boolean refresh() {
        BuiltinCommandBlockerSettings settings = BuiltinCommandBlockerSettings.load(feature.getConfigHandler());
        Map<String, Command> liveCommands = commandMap.getKnownCommands();
        boolean commandMapChanged = BuiltinCommandMapRemoval.pruneDisabledPluginCommands(removedCommands);

        if (!settings.removeFromCommandMap() && !removedCommands.isEmpty()) {
            commandMapChanged |= BuiltinCommandMapRemoval.restoreAll(liveCommands, removedCommands);
        }

        Map<String, Command> effectiveCommands = BuiltinCommandMapRemoval.effectiveCommands(
                liveCommands,
                removedCommands
        );
        BuiltinCommandSnapshot next = BuiltinCommandDiscovery.discover(
                effectiveCommands,
                feature.getPlugin().getServer().getCommandAliases(),
                settings
        );

        if (settings.removeFromCommandMap()) {
            commandMapChanged |= BuiltinCommandMapRemoval.reconcile(
                    liveCommands,
                    removedCommands,
                    next.blockedCommands()
            );
        }

        BuiltinCommandSnapshot previous = snapshot;
        snapshot = next;
        try {
            persistGeneratedSnapshot(next);
        } catch (RuntimeException exception) {
            feature.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Could not update BuiltinCommandBlocker generated command diagnostics.",
                    exception
            );
        }
        return commandMapChanged || !next.equals(previous);
    }

    public void refreshAndUpdatePlayers() {
        if (refresh()) {
            updatePlayers();
        }
    }

    public void updatePlayers() {
        for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    public void restoreRemovedCommands() {
        if (removedCommands.isEmpty()) {
            return;
        }
        BuiltinCommandMapRemoval.restoreAll(commandMap.getKnownCommands(), removedCommands);
    }

    public void removeBlockedCommands(Collection<String> commands) {
        BuiltinCommandSnapshot current = snapshot;
        commands.removeIf(current::isBlocked);
    }

    public boolean isBlockedCommandLine(String commandLine) {
        return snapshot.isBlocked(rootCommand(commandLine));
    }

    public BuiltinCommandSnapshot snapshot() {
        return snapshot;
    }

    static String rootCommand(String commandLine) {
        String normalized = BuiltinCommandBlockerSettings.normalizeCommand(commandLine);
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) {
                return normalized.substring(0, index);
            }
        }
        return normalized;
    }

    private void persistGeneratedSnapshot(BuiltinCommandSnapshot current) {
        FeatureConfigHandler config = feature.getConfigHandler();
        int count = current.blockedCommandCount();
        List<String> commands = current.sortedBlockedCommands();
        Map<String, Integer> sources = current.detectedSources();

        int storedCount = config.get(GENERATED_COUNT, Integer.class, -1);
        List<String> storedCommands = config.getList(GENERATED_COMMANDS, String.class, List.of());
        Map<String, Integer> storedSources = config.getMapValues(GENERATED_SOURCES, Integer.class, Map.of());

        if (storedCount == count && storedCommands.equals(commands) && storedSources.equals(sources)) {
            return;
        }

        config.batch(batch -> batch
                .put(GENERATED_COUNT, count)
                .put(GENERATED_COMMANDS, commands)
                .put(GENERATED_SOURCES, new LinkedHashMap<>(sources))
        );
    }
}
