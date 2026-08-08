package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import nl.hauntedmc.serverfeatures.framework.port.VanishVisibilityPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Owns local join and quit messages while protecting persisted vanish state. */
public final class NotificationHandler {
    private static final long VANISH_RESOLUTION_TIMEOUT_TICKS = 100L;

    private final NotifyLogin feature;
    private final ConnectionMessageSettings settings;
    private final AtomicLong generationSequence = new AtomicLong();
    private final Map<UUID, Long> pendingJoins = new ConcurrentHashMap<>();
    private final Map<UUID, VisibilityState> knownVisibility = new ConcurrentHashMap<>();

    public NotificationHandler(NotifyLogin feature, ConnectionMessageSettings settings) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void handleJoin(PlayerJoinEvent event) {
        event.joinMessage(null);

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        long generation = generationSequence.incrementAndGet();
        pendingJoins.put(playerUuid, generation);
        knownVisibility.put(playerUuid, VisibilityState.PENDING);

        Optional<VanishVisibilityPort> vanishApi;
        try {
            vanishApi = feature.getPlugin().getInternalServiceRegistry().find(VanishVisibilityPort.class);
        } catch (Throwable throwable) {
            completeJoin(playerUuid, generation, null, VisibilityState.UNKNOWN, throwable);
            return;
        }
        if (vanishApi.isEmpty()) {
            completeJoin(playerUuid, generation, null, VisibilityState.VISIBLE, null);
            return;
        }

        VanishVisibilityPort api = vanishApi.get();
        CompletionStage<Boolean> initialState;
        try {
            initialState = api.resolveInitialVanishState(playerUuid);
        } catch (Throwable throwable) {
            completeJoin(playerUuid, generation, api, VisibilityState.UNKNOWN, throwable);
            return;
        }
        if (initialState == null) {
            completeJoin(
                    playerUuid,
                    generation,
                    api,
                    VisibilityState.UNKNOWN,
                    new IllegalStateException("Vanish returned no initial-state completion stage.")
            );
            return;
        }

        try {
            initialState.whenComplete((initiallyVanished, throwable) -> {
                try {
                    feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                        VisibilityState state;
                        if (throwable != null || initiallyVanished == null) {
                            state = VisibilityState.UNKNOWN;
                        } else {
                            state = initiallyVanished ? VisibilityState.HIDDEN : VisibilityState.VISIBLE;
                        }
                        completeJoin(playerUuid, generation, api, state, throwable);
                    });
                } catch (Throwable schedulingFailure) {
                    completeJoin(playerUuid, generation, api, VisibilityState.UNKNOWN, schedulingFailure);
                }
            });
        } catch (Throwable callbackFailure) {
            completeJoin(playerUuid, generation, api, VisibilityState.UNKNOWN, callbackFailure);
            return;
        }

        try {
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> completeJoin(
                            playerUuid,
                            generation,
                            api,
                            VisibilityState.UNKNOWN,
                            new TimeoutException("Initial vanish-state resolution exceeded "
                                    + VANISH_RESOLUTION_TIMEOUT_TICKS + " ticks.")
                    ),
                    BukkitTime.ticks(VANISH_RESOLUTION_TIMEOUT_TICKS)
            );
        } catch (Throwable schedulingFailure) {
            completeJoin(playerUuid, generation, api, VisibilityState.UNKNOWN, schedulingFailure);
        }
    }

    public void handleQuit(PlayerQuitEvent event) {
        event.quitMessage(null);

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        boolean joinWasPending = pendingJoins.remove(playerUuid) != null;
        VisibilityState visibility = knownVisibility.remove(playerUuid);

        Optional<VanishVisibilityPort> vanishApi;
        try {
            vanishApi = feature.getPlugin().getInternalServiceRegistry().find(VanishVisibilityPort.class);
        } catch (Throwable throwable) {
            feature.getLogger().warning("Could not query vanish state while " + playerUuid
                    + " was leaving; suppressing the quit message: " + rootMessage(throwable));
            return;
        }

        Boolean currentlyVanished = null;
        if (vanishApi.isPresent()) {
            try {
                currentlyVanished = vanishApi.get().isVanished(playerUuid);
            } catch (Throwable throwable) {
                feature.getLogger().warning("Could not query current vanish state while " + playerUuid
                        + " was leaving; suppressing the quit message: " + rootMessage(throwable));
                return;
            }
        }

        if (shouldSuppressQuit(joinWasPending, visibility, currentlyVanished)) {
            return;
        }

        broadcast(
                player,
                ConnectionMessageSettings.EventType.QUIT,
                AnnouncementOrigin.REAL_CONNECTION
        );
    }

    public void handleVanishStateChange(Player player, boolean vanished) {
        Objects.requireNonNull(player, "player");

        UUID playerUuid = player.getUniqueId();
        boolean joinWasPending = pendingJoins.remove(playerUuid) != null;
        VisibilityState previousVisibility = knownVisibility.put(
                playerUuid,
                vanished ? VisibilityState.HIDDEN : VisibilityState.VISIBLE
        );

        if (!settings.announceVanishStateChanges()
                || !shouldBroadcastVanishTransition(vanished, joinWasPending, previousVisibility)) {
            return;
        }

        broadcast(
                player,
                vanished ? ConnectionMessageSettings.EventType.QUIT : ConnectionMessageSettings.EventType.JOIN,
                AnnouncementOrigin.VANISH_TRANSITION
        );
    }

    public void close() {
        pendingJoins.clear();
        knownVisibility.clear();
    }

    static boolean shouldSuppressQuit(
            boolean joinWasPending,
            VisibilityState rememberedVisibility,
            Boolean currentlyVanished
    ) {
        if (joinWasPending
                || rememberedVisibility == VisibilityState.PENDING
                || rememberedVisibility == VisibilityState.UNKNOWN) {
            return true;
        }
        if (Boolean.TRUE.equals(currentlyVanished)) {
            return true;
        }
        return rememberedVisibility == VisibilityState.HIDDEN && currentlyVanished == null;
    }

    static boolean shouldBroadcastVanishTransition(
            boolean vanished,
            boolean joinWasPending,
            VisibilityState previousVisibility
    ) {
        if (!vanished) {
            return previousVisibility != VisibilityState.VISIBLE;
        }
        return !joinWasPending
                && previousVisibility != VisibilityState.PENDING
                && previousVisibility != VisibilityState.UNKNOWN
                && previousVisibility != VisibilityState.HIDDEN;
    }

    static boolean shouldExcludeSubject(
            ConnectionMessageSettings.EventType eventType,
            AnnouncementOrigin origin
    ) {
        return eventType == ConnectionMessageSettings.EventType.QUIT
                && origin == AnnouncementOrigin.REAL_CONNECTION;
    }

    private void completeJoin(
            UUID playerUuid,
            long generation,
            VanishVisibilityPort vanishApi,
            VisibilityState visibility,
            Throwable failure
    ) {
        if (!pendingJoins.remove(playerUuid, generation)) {
            return;
        }
        knownVisibility.put(playerUuid, visibility);

        if (failure != null) {
            feature.getLogger().warning("Could not resolve initial vanish state for " + playerUuid
                    + "; suppressing the join message: " + rootMessage(failure));
        }
        if (visibility != VisibilityState.VISIBLE) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (vanishApi != null) {
            try {
                if (vanishApi.isVanished(playerUuid)) {
                    knownVisibility.put(playerUuid, VisibilityState.HIDDEN);
                    return;
                }
            } catch (Throwable throwable) {
                knownVisibility.put(playerUuid, VisibilityState.UNKNOWN);
                feature.getLogger().warning("Could not verify current vanish state for " + playerUuid
                        + "; suppressing the join message: " + rootMessage(throwable));
                return;
            }
        }

        broadcast(
                player,
                ConnectionMessageSettings.EventType.JOIN,
                AnnouncementOrigin.REAL_CONNECTION
        );
    }

    private void broadcast(
            Player subject,
            ConnectionMessageSettings.EventType eventType,
            AnnouncementOrigin origin
    ) {
        ConnectionMessageSettings.Resolution resolution;
        try {
            resolution = settings.resolve(
                    subject.getUniqueId(),
                    subject.getName(),
                    subject::hasPermission,
                    eventType
            );
        } catch (Throwable throwable) {
            feature.getLogger().warning("Could not resolve the NotifyLogin message for "
                    + subject.getUniqueId() + ": " + rootMessage(throwable));
            return;
        }
        if (resolution.suppressed()) {
            return;
        }

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getUniqueId().equals(subject.getUniqueId())
                    && shouldExcludeSubject(eventType, origin)) {
                continue;
            }
            if (!canSee(recipient, subject)) {
                continue;
            }

            try {
                recipient.sendMessage(feature.getLocalizationHandler()
                        .getMessage(resolution.messageKey())
                        .forAudience(recipient)
                        .withPlaceholderPlayer(subject)
                        .with("name", subject.getName())
                        .with("display_name", subject.displayName())
                        .with("uuid", subject.getUniqueId().toString())
                        .with("profile", resolution.source())
                        .build());
            } catch (Throwable throwable) {
                feature.getLogger().warning("Could not deliver a NotifyLogin message about "
                        + subject.getUniqueId() + " to " + recipient.getUniqueId() + ": "
                        + rootMessage(throwable));
            }
        }
    }

    private static boolean canSee(Player recipient, Player subject) {
        try {
            return recipient.getUniqueId().equals(subject.getUniqueId()) || recipient.canSee(subject);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    enum AnnouncementOrigin {
        REAL_CONNECTION,
        VANISH_TRANSITION
    }

    enum VisibilityState {
        PENDING,
        VISIBLE,
        HIDDEN,
        UNKNOWN
    }
}
