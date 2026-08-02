package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.config.AutoPickupSettings;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.LoadState;
import nl.hauntedmc.serverfeatures.features.autopickup.persistence.AutoPickupPreferenceRepository.StoredPreference;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    private final PlayerIdentityResolver identityResolver;
    private final AutoPickupWriteRevisionClock revisionClock = new AutoPickupWriteRevisionClock();
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
        this.identityResolver = new PlayerIdentityResolver(feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for AutoPickup.")));
    }

    public void initialize(Player player) {
        if (closed.get()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        AutoPickupPlayerState state = new AutoPickupPlayerState();
        long generation = state.nextGeneration();
        states.put(uuid, state);

        CompletableFuture<Optional<PlayerIdentity>> identityFuture = identityResolver.whenReady(uuid);
        track(identityFuture);
        identityFuture.whenComplete((identity, throwable) -> scheduleMain(
                () -> completeIdentity(uuid, state, generation, identity, throwable)
        ));
    }

    public void remove(Player player) {
        states.remove(player.getUniqueId());
    }

    public boolean isEnabled(Player player) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        return state != null
                && state.loadState() == LoadState.READY
                && state.enabled()
                && (!settings.requireUsePermission() || player.hasPermission(AutoPickup.USE_PERMISSION));
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
            if (state.playerId() > 0L
                    && (intent == CommandIntent.ENABLE || intent == CommandIntent.DISABLE)) {
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
        long previousNotice = state.lastFullNoticeNanos();
        if (previousNotice != Long.MIN_VALUE
                && nowNanos - previousNotice < notification.cooldownNanos()) {
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
            state.nextGeneration();
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

    private void completeIdentity(UUID uuid,
                                  AutoPickupPlayerState state,
                                  long generation,
                                  Optional<PlayerIdentity> identity,
                                  Throwable throwable) {
        AutoPickupPlayerState current = states.get(uuid);
        if (closed.get() || current != state || current.generation() != generation) {
            return;
        }
        if (throwable != null || identity == null || identity.isEmpty()) {
            current.loadState(LoadState.FAILED);
            current.enabled(false);
            current.persisted(false);
            CommandIntent pending = current.pendingCommand();
            current.pendingCommand(null);
            if (throwable != null) {
                feature.getLogger().log(
                        Level.WARNING,
                        "Failed to resolve AutoPickup identity for " + uuid,
                        throwable
                );
            } else {
                feature.getLogger().warning("No canonical DataRegistry identity was available for " + uuid);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && pending != null) {
                send(player, "autopickup.load_failed");
            }
            return;
        }

        current.playerId(identity.get().playerId());
        WriteSlot existingWrite = writes.get(uuid);
        WriteRequest newestLocalRequest = existingWrite == null ? null : existingWrite.newestRequest();
        if (newestLocalRequest != null) {
            revisionClock.observe(newestLocalRequest.writeRevision());
            finishLoad(
                    uuid,
                    current,
                    newestLocalRequest.enabled(),
                    false,
                    newestLocalRequest.writeRevision(),
                    false
            );
            return;
        }
        load(uuid, state, generation, current.playerId());
    }

    private void load(UUID uuid,
                      AutoPickupPlayerState state,
                      long generation,
                      long playerId) {
        CompletableFuture<Optional<StoredPreference>> future = feature.getLifecycleManager()
                .getTaskManager()
                .supplyAsync(() -> repository.load(playerId));
        track(future);
        future.whenComplete((loaded, throwable) -> scheduleMain(() -> {
            AutoPickupPlayerState current = states.get(uuid);
            if (closed.get() || current != state || current.generation() != generation) {
                return;
            }
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
                Player player = Bukkit.getPlayer(uuid);
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

            StoredPreference stored = loaded.orElse(null);
            if (stored == null) {
                finishLoad(uuid, current, settings.defaultEnabled(), true, 0L, true);
            } else {
                revisionClock.observe(stored.writeRevision());
                finishLoad(uuid, current, stored.enabled(), true, stored.writeRevision(), true);
            }
        }));
    }

    private void finishLoad(UUID uuid,
                            AutoPickupPlayerState state,
                            boolean enabled,
                            boolean persisted,
                            long writeRevision,
                            boolean scheduleRecheck) {
        state.enabled(enabled);
        state.persisted(persisted);
        state.writeRevision(writeRevision);
        state.loadState(LoadState.READY);
        CommandIntent pending = state.pendingCommand();
        state.pendingCommand(null);
        Player player = Bukkit.getPlayer(uuid);
        if (pending != null && player != null && player.isOnline()) {
            apply(player, state, pending);
        }
        if (scheduleRecheck && settings.joinRecheckDelayMillis() > 0L) {
            schedulePreferenceRecheck(uuid, state, state.generation());
        }
    }

    private void schedulePreferenceRecheck(UUID uuid,
                                           AutoPickupPlayerState state,
                                           long generation) {
        try {
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> beginPreferenceRecheck(uuid, state, generation),
                    BukkitTime.milliseconds(settings.joinRecheckDelayMillis())
            );
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                feature.getLogger().warning(
                        "Could not schedule AutoPickup backend-switch recheck: " + rootMessage(exception)
                );
            }
        }
    }

    private void beginPreferenceRecheck(UUID uuid,
                                        AutoPickupPlayerState state,
                                        long generation) {
        Player player = Bukkit.getPlayer(uuid);
        if (closed.get()
                || states.get(uuid) != state
                || state.generation() != generation
                || state.loadState() != LoadState.READY
                || writes.containsKey(uuid)
                || player == null
                || !player.isOnline()) {
            return;
        }

        CompletableFuture<Optional<StoredPreference>> future = feature.getLifecycleManager()
                .getTaskManager()
                .supplyAsync(() -> repository.load(state.playerId()));
        track(future);
        future.whenComplete((loaded, throwable) -> scheduleMain(
                () -> completePreferenceRecheck(uuid, state, generation, loaded, throwable)
        ));
    }

    private void completePreferenceRecheck(UUID uuid,
                                           AutoPickupPlayerState state,
                                           long generation,
                                           Optional<StoredPreference> loaded,
                                           Throwable throwable) {
        if (closed.get()
                || states.get(uuid) != state
                || state.generation() != generation
                || state.loadState() != LoadState.READY
                || writes.containsKey(uuid)) {
            return;
        }
        if (throwable != null) {
            feature.getLogger().log(
                    Level.FINE,
                    "AutoPickup backend-switch preference recheck failed for " + uuid,
                    throwable
            );
            return;
        }

        StoredPreference stored = loaded == null ? null : loaded.orElse(null);
        if (stored == null || stored.writeRevision() <= state.writeRevision()) {
            return;
        }

        revisionClock.observe(stored.writeRevision());
        boolean changed = state.enabled() != stored.enabled();
        state.enabled(stored.enabled());
        state.persisted(true);
        state.writeRevision(stored.writeRevision());
        state.nextGeneration();
        Player player = Bukkit.getPlayer(uuid);
        if (changed && player != null && player.isOnline()) {
            send(player, stored.enabled()
                    ? "autopickup.remote_enabled"
                    : "autopickup.remote_disabled");
        }
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
            if (!state.persisted() && (intent == CommandIntent.ENABLE || intent == CommandIntent.DISABLE)) {
                send(player, "autopickup.save_retry");
                requestSave(player.getUniqueId(), state.playerId(), desired);
            } else {
                send(player, desired ? "autopickup.already_enabled" : "autopickup.already_disabled");
            }
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
        slot.queuedRequest = new WriteRequest(desired, revisionClock.next());
        if (!slot.inFlight) {
            startNextWrite(uuid, slot);
        }
    }

    private void startNextWrite(UUID uuid, WriteSlot slot) {
        if (closed.get() || slot.queuedRequest == null) {
            writes.remove(uuid, slot);
            return;
        }
        WriteRequest request = slot.queuedRequest;
        slot.queuedRequest = null;
        slot.inFlight = true;
        slot.activeRequest = request;
        attemptWrite(uuid, slot, request, 1);
    }

    private void attemptWrite(UUID uuid, WriteSlot slot, WriteRequest request, int attempt) {
        if (closed.get()) {
            slot.inFlight = false;
            return;
        }
        CompletableFuture<StoredPreference> future = feature.getLifecycleManager().getTaskManager().supplyAsync(
                () -> repository.upsert(slot.playerId, request.enabled(), request.writeRevision())
        );
        track(future);
        future.whenComplete((stored, throwable) -> {
            if (throwable == null) {
                scheduleMain(() -> completeWrite(uuid, slot, request, stored));
                return;
            }
            if (attempt < settings.retry().attempts() && !closed.get()) {
                long delay = settings.retry().delayForAttempt(attempt - 1);
                try {
                    feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                            () -> attemptWrite(uuid, slot, request, attempt + 1),
                            BukkitTime.milliseconds(delay)
                    );
                } catch (RuntimeException schedulingFailure) {
                    throwable.addSuppressed(schedulingFailure);
                    scheduleMain(() -> failWrite(uuid, slot, request, throwable));
                }
            } else {
                scheduleMain(() -> failWrite(uuid, slot, request, throwable));
            }
        });
    }

    private void completeWrite(UUID uuid,
                               WriteSlot slot,
                               WriteRequest request,
                               StoredPreference stored) {
        if (writes.get(uuid) != slot) {
            return;
        }
        slot.inFlight = false;
        slot.activeRequest = null;
        revisionClock.observe(stored.writeRevision());

        boolean accepted = stored.writeRevision() == request.writeRevision()
                && stored.enabled() == request.enabled();
        AutoPickupPlayerState state = states.get(uuid);
        if (state != null) {
            state.writeRevision(Math.max(state.writeRevision(), stored.writeRevision()));
        }
        if (accepted) {
            if (state != null && state.enabled() == request.enabled() && slot.queuedRequest == null) {
                state.persisted(true);
            }
        } else if (slot.queuedRequest == null
                && state != null
                && state.loadState() == LoadState.READY
                && state.enabled() == request.enabled()) {
            boolean changed = state.enabled() != stored.enabled();
            state.enabled(stored.enabled());
            state.persisted(true);
            state.nextGeneration();
            Player player = Bukkit.getPlayer(uuid);
            if (changed && player != null && player.isOnline()) {
                send(player, stored.enabled()
                        ? "autopickup.remote_enabled"
                        : "autopickup.remote_disabled");
            }
        }

        if (slot.queuedRequest != null) {
            startNextWrite(uuid, slot);
        } else {
            writes.remove(uuid, slot);
        }
    }

    private void failWrite(UUID uuid,
                           WriteSlot slot,
                           WriteRequest request,
                           Throwable throwable) {
        if (writes.get(uuid) != slot) {
            return;
        }
        slot.inFlight = false;
        slot.activeRequest = null;
        feature.getLogger().log(
                Level.WARNING,
                "Failed to persist AutoPickup=" + request.enabled() + " for player " + uuid,
                throwable
        );
        if (slot.queuedRequest != null) {
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
        future.thenRun(() -> activeAttempts.remove(future));
        future.exceptionally(failure -> {
            if (failure != null) {
                activeAttempts.remove(future);
            }
            return null;
        });
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

    private record WriteRequest(boolean enabled, long writeRevision) {
        private WriteRequest {
            if (writeRevision <= 0L) {
                throw new IllegalArgumentException("writeRevision must be positive");
            }
        }
    }

    private static final class WriteSlot {
        private long playerId;
        private boolean inFlight;
        private WriteRequest activeRequest;
        private WriteRequest queuedRequest;

        private WriteSlot(long playerId) {
            this.playerId = playerId;
        }

        private WriteRequest newestRequest() {
            return queuedRequest != null ? queuedRequest : activeRequest;
        }
    }
}
