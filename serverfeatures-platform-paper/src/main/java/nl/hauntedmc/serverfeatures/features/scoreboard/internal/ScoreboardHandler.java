package nl.hauntedmc.serverfeatures.features.scoreboard.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.scoreboard.Scoreboard;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Generates localized scoreboard content and isolates third-party rendering failures. */
public class ScoreboardHandler {
    private static final int MAX_LINES = 15;
    private static final String[] LINE_KEYS = createLineKeys();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final Scoreboard feature;
    private final LocalizationHandler i18n;
    private final Map<UUID, ScoreboardSnapshot> lastScoreboards = new ConcurrentHashMap<>();
    private final ConcurrentMap<FailureKey, Long> lastWarnings = new ConcurrentHashMap<>();
    private final int refreshInterval;

    public ScoreboardHandler(Scoreboard feature) {
        this.feature = feature;
        this.i18n = feature.getLocalizationHandler();
        this.refreshInterval = (int) feature.getConfigHandler().get("refresh_interval");
    }

    /** Immediately recalculates and pushes the sidebar for one player. */
    public void updateScoreboardContent(Player player) {
        LocalizationHandler.PlayerMessages messages = i18n.messagesFor(player);
        Component title = renderMessageSafely("scoreboard.title", player, messages);
        if (title == null) {
            title = Component.empty();
        }

        List<Component> lines = new ArrayList<>(MAX_LINES);
        for (String messageKey : LINE_KEYS) {
            Component line = renderMessageSafely(messageKey, player, messages);
            if (line == null) {
                continue;
            }
            String plain;
            try {
                plain = PLAIN_TEXT.serialize(line);
            } catch (RuntimeException | LinkageError failure) {
                reportFailure(player, messageKey + ".serialize", failure);
                continue;
            }
            if (plain.startsWith("<end>")) {
                break;
            }
            lines.add(line);
        }

        UUID playerId = player.getUniqueId();
        ScoreboardSnapshot previous = lastScoreboards.get(playerId);
        ScoreboardSnapshot next = new ScoreboardSnapshot(title, List.copyOf(lines));
        if (next.equals(previous)) {
            return;
        }

        ScoreboardManager.updateSidebar(
                player,
                title,
                lines,
                previous == null ? null : previous.lines()
        );
        lastScoreboards.put(playerId, next);
    }

    /** Updates one player without allowing a rendering or provider failure to escape to an event. */
    public void updateScoreboardSafely(Player player) {
        try {
            updateScoreboardContent(player);
        } catch (RuntimeException | LinkageError failure) {
            reportFailure(player, "scoreboard.update", failure);
        }
    }

    /** Runs one independently guarded update for every online player. */
    public void startUpdater() {
        feature.getLifecycleManager().getTaskManager()
                .scheduleRepeatingTask(() -> updatePlayersIndependently(
                                Bukkit.getOnlinePlayers(),
                                this::updateScoreboardContent,
                                (player, failure) -> reportFailure(player, "scoreboard.update", failure)
                        ),
                        BukkitTime.ticks(0L), BukkitTime.ticks(refreshInterval));
    }

    /** Removes the player's scoreboard from the handler. */
    public void removePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        lastScoreboards.remove(playerId);
        lastWarnings.keySet().removeIf(key -> key.playerId().equals(playerId));
        try {
            ScoreboardManager.removeSidebar(player);
        } catch (RuntimeException | LinkageError failure) {
            reportFailure(player, "scoreboard.remove", failure);
        }
    }

    /** Removes all tracked sidebars while isolating failures per player. */
    public void removeAllPlayers() {
        lastScoreboards.clear();
        lastWarnings.clear();
        updatePlayersIndependently(
                Bukkit.getOnlinePlayers(),
                ScoreboardManager::removeSidebar,
                (player, failure) -> reportFailure(player, "scoreboard.remove", failure)
        );
    }

    private Component renderMessageSafely(
            String messageKey,
            Player player,
            LocalizationHandler.PlayerMessages messages
    ) {
        try {
            return messages.build(messageKey);
        } catch (RuntimeException | LinkageError failure) {
            reportFailure(player, messageKey, failure);
            return null;
        }
    }

    private static String[] createLineKeys() {
        String[] keys = new String[MAX_LINES];
        for (int index = 0; index < MAX_LINES; index++) {
            keys[index] = "scoreboard.line" + (index + 1);
        }
        return keys;
    }

    private void reportFailure(Player player, String messageKey, Throwable failure) {
        UUID playerId = safePlayerId(player);
        FailureKey key = new FailureKey(playerId, messageKey, failure.getClass().getName());
        long now = System.nanoTime();
        AtomicBoolean emit = new AtomicBoolean();
        lastWarnings.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= WARNING_INTERVAL_NANOS) {
                emit.set(true);
                return now;
            }
            return previous;
        });
        if (!emit.get()) {
            return;
        }
        feature.getPlugin().getLogger().log(
                Level.WARNING,
                "[Scoreboard] Failed to render '" + messageKey + "' for player '"
                        + safePlayerName(player) + "'; the remaining scoreboard update will continue.",
                failure
        );
    }

    private static UUID safePlayerId(Player player) {
        try {
            UUID uniqueId = player.getUniqueId();
            if (uniqueId != null) {
                return uniqueId;
            }
        } catch (RuntimeException ignored) {
            // Use an instance-derived UUID solely for warning suppression.
        }
        return new UUID(0L, Integer.toUnsignedLong(System.identityHashCode(player)));
    }

    private static String safePlayerName(Player player) {
        try {
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Use a non-identifying fallback.
        }
        return "unknown";
    }

    static void updatePlayersIndependently(
            Iterable<? extends Player> players,
            Consumer<Player> updater,
            BiConsumer<Player, Throwable> failureHandler
    ) {
        for (Player player : players) {
            try {
                updater.accept(player);
            } catch (RuntimeException | LinkageError failure) {
                failureHandler.accept(player, failure);
            }
        }
    }

    private record ScoreboardSnapshot(Component title, List<Component> lines) {
    }

    private record FailureKey(UUID playerId, String messageKey, String failureType) {
    }
}
