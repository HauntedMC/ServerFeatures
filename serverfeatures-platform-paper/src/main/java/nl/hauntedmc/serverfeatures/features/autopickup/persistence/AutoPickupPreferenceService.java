package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.config.AutoPickupSettings;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.LoadState;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Main-thread player state plus feature-scoped asynchronous persistence.
 */
public final class AutoPickupPreferenceService {

    private final AutoPickup feature;
    private final AutoPickupSettings settings;
    private final AutoPickupPreferenceRepository repository;
    private final Map<UUID, AutoPickupPlayerState> states = new HashMap<>();
    private final Map<UUID, WriteSlot> writes = new HashMap<>();
    private final Set<CompletableFuture<?>> activeAttempts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AutoPickupPreferenceService(AutoPickup feature,
                                       AutoPickupSettings settings,
                                       AutoPickupPreferenceRepository repository) {
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
    }

    public void initialize(Player player) {
        if (closed.get()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        AutoPickupPlayerState state = new AutoPickupPlayerState();
        long generation = state.nextGeneration();
        states.put(uuid, state);

        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                (readyPlayer, identity) -> {
                    AutoPickupPlayerState current = states.get(uuid);
                    if (current != state || current.generation() != generation || closed.get()) {
                        return;
                    }
                    current.playerId(identity.playerId());
                    load(uuid, state, generation, identity.playerId());
                },
                "AutoPickup preference load"
        );
    }

    public void remove(Player player) {
        states.remove(player.getUniqueId());
    }

    public boolean isEnabled(Player player) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        return state != null
                && state.loadState() == LoadState.READY
                && state.enabled()
                && player.hasPermission(AutoPickup.USE_PERMISSION);
    }

    public AutoPickupPlayerState state(Player player) {
        return states.get(player.getUniqueId());
    }

    public void handleCommand(Player player, CommandIntent intent) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        if (state == null) {
            initialize(player);
            state = states.get(player.getUniqueId());
        }
        if (state == null) {
            send(player, "autopickup.load_failed");
            return;
        }

        if (state.loadState() == LoadState.LOADING) {
            if (intent == CommandIntent.STATUS) {
                send(player, "autopickup.status.loading");
            } else {
                state.pendingCommand(intent);
                send(player, "autopickup.command_queued");
            }
            return;
        }

        if (state.loadState() == LoadState.FAILED) {
            if (intent == CommandIntent.ENABLE || intent == CommandIntent.DISABLE) {
                state.loadState(LoadState.READY);
                apply(player, state, intent);
            } else {
                send(player, "autopickup.load_failed");
            }
            return;
        }

        apply(player, state, intent);
    }

    public boolean shouldNotifyFull(Player player, boolean partialInsertion, long nowNanos) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        AutoPickupSettings.NotificationSettings notification = settings.notification();
        if (state == null || !notification.enabled()) {
            return false;
        }
        if (partialInsertion && !notification.notifyOnPartial()) {
            return false;
        }
        long elapsed = nowNanos - state.lastFullNoticeNanos();
        if (state.lastFullNoticeNanos() != Long.MIN_VALUE && elapsed < notification.cooldownNanos()) {
            return false;
        }
        state.lastFullNoticeNanos(nowNanos);
        return true;
    }

    public void disableForSession(Player player) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        if (state != null) {
            state.enabled(false);
            state.persisted(false);
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        states.clear();
        CompletableFuture<?>[] attempts = activeAttempts.toArray(CompletableFuture[]::new);
        if (attempts.length > 0 && settings.shutdownDrainTimeoutMillis() > 0L) {
            try {
                CompletableFuture.allOf(attempts)
                        .get(settings.shutdownDrainTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception exception) {
                feature.getLogger().warning(
                        "AutoPickup persistence did not fully drain before feature shutdown: "
                                + rootMessage(exception)
                );
            }
        }
        writes.clear();
    }

    private void load(UUID uuid,
                      AutoPickupPlayerState state,
                      long generation,
                      long playerId) {
        CompletableFuture<java.util.Optional<Boolean>> future = feature.getLifecycleManager()
                .getTaskManager()
                .supplyAsync(() -> repository.load(playerId));
        track(future);
        future.whenComplete((loaded, throwable) -> scheduleMain(() -> {
            AutoPickupPlayerState current = states.get(uuid);
            if (closed.get() || current != state || current.generation() != generation) {
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (throwable != null) {
                current.loadState(LoadState.FAILED);
                current.enabled(false);
                current.persisted(false);
                feature.getLogger().log(
                        Level.WARNING,
                        "Failed to load AutoPickup preference for " + uuid,
                        throwable
                );
                CommandIntent pending = current.pendingCommand();
                current.pendingCommand(null);
                if (player != null && player.isOnline()) {
                    if (pending == CommandIntent.ENABLE || pending == CommandIntent.DISABLE) {
                        current.loadState(LoadState.READY);
                        apply(player, current, pending);
                    } else {
                        send(player, "autopickup.load_failed");
                    }
                }
                return;
            }

            current.enabled(loaded.orElse(settings.defaultEnabled()));
            current.persisted(true);
            current.loadState(LoadState.READY);
            CommandIntent pending = current.pendingCommand();
            current.pendingCommand(null);
            if (pending != null && player != null && player.isOnline()) {
                apply(player, current, pending);
            }
        }));
    }

    private void apply(Player player, AutoPickupPlayerState state, CommandIntent intent) {
        if (intent == CommandIntent.STATUS) {
            String key = state.enabled() ? "autopickup.status.enabled" : "autopickup.status.disabled";
            send(player, key);
            if (!state.persisted()) {
                send(player, "autopickup.status.unsaved");
            }
            return;
        }

        boolean desired = switch (intent) {
            case ENABLE -> true;
            case DISABLE -> false;
            case TOGGLE -> !state.enabled();
            case STATUS -> throw new IllegalStateException("STATUS was handled before state mutation");
        };

        if (desired == state.enabled()) {
            send(player, desired ? "autopickup.already_enabled" : "autopickup.already_disabled");
            return;
        }

        state.enabled(desired);
        state.persisted(false);
        state.nextGeneration();
        send(player, desired ? "autopickup.enabled" : "autopickup.disabled");
        requestSave(player.getUniqueId(), state.playerId(), desired);
    }

    private void requestSave(UUID uuid, long playerId, boolean desired) {
        if (closed.get() || playerId <= 0L) {
            return;
        }
        WriteSlot slot = writes.computeIfAbsent(uuid, ignored -> new WriteSlot(playerId));
        slot.playerId = playerId;
        slot.queuedValue = desired;
        if (!slot.inFlight) {
            startNextWrite(uuid, slot);
        }
    }

    private void startNextWrite(UUID uuid, WriteSlot slot) {
        if (closed.get() || slot.queuedValue == null) {
            writes.remove(uuid, slot);
            return;
        }
        boolean value = slot.queuedValue;
        slot.queuedValue = null;
        slot.inFlight = true;
        slot.activeValue = value;
        attemptWrite(uuid, slot, value, 1);
    }

    private void attemptWrite(UUID uuid, WriteSlot slot, boolean value, int attempt) {
        if (closed.get()) {
            slot.inFlight = false;
            return;
        }
        CompletableFuture<Void> future = feature.getLifecycleManager().getTaskManager().runAsync(
                () -> repository.upsert(slot.playerId, value)
        );
        track(future);
        future.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                scheduleMain(() -> completeWrite(uuid, slot, value));
                return;
            }
            if (attempt < settings.retry().attempts() && !closed.get()) {
                long delay = settings.retry().delayForAttempt(attempt - 1);
                try {
                    feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                            () -> attemptWrite(uuid, slot, value, attempt + 1),
                            BukkitTime.milliseconds(delay)
                    );
                } catch (RuntimeException schedulingFailure) {
                    throwable.addSuppressed(schedulingFailure);
                    scheduleMain(() -> failWrite(uuid, slot, value, throwable));
                }
            } else {
                scheduleMain(() -> failWrite(uuid, slot, value, throwable));
            }
        });
    }

    private void completeWrite(UUID uuid, WriteSlot slot, boolean value) {
        if (writes.get(uuid) != slot) {
            return;
        }
        slot.inFlight = false;
        AutoPickupPlayerState state = states.get(uuid);
        if (state != null && state.enabled() == value
                && (slot.queuedValue == null || slot.queuedValue == value)) {
            state.persisted(true);
        }
        if (slot.queuedValue != null && slot.queuedValue != value) {
            startNextWrite(uuid, slot);
        } else {
            slot.queuedValue = null;
            writes.remove(uuid, slot);
        }
    }

    private void failWrite(UUID uuid, WriteSlot slot, boolean value, Throwable throwable) {
        if (writes.get(uuid) != slot) {
            return;
        }
        slot.inFlight = false;
        feature.getLogger().log(
                Level.WARNING,
                "Failed to persist AutoPickup=" + value + " for player " + uuid,
                throwable
        );
        if (slot.queuedValue != null && slot.queuedValue != value) {
            startNextWrite(uuid, slot);
            return;
        }
        writes.remove(uuid, slot);
        AutoPickupPlayerState state = states.get(uuid);
        if (state != null) {
            state.persisted(false);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            send(player, "autopickup.save_failed");
        }
    }

    private void track(CompletableFuture<?> future) {
        activeAttempts.add(future);
        future.whenComplete((ignored, throwable) -> activeAttempts.remove(future));
    }

    private void scheduleMain(Runnable runnable) {
        if (closed.get()) {
            return;
        }
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(runnable);
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                feature.getLogger().warning(
                        "Could not schedule AutoPickup persistence completion: " + rootMessage(exception)
                );
            }
        }
    }

    private void send(Player player, String key) {
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static final class WriteSlot {
        private long playerId;
        private boolean inFlight;
        private boolean activeValue;
        private Boolean queuedValue;

        private WriteSlot(long playerId) {
            this.playerId = playerId;
        }
    }
}
