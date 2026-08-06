package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyBalanceMessage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Maintains the bounded cache used by online-player UI and placeholders.
 *
 * <p>Database reads remain authoritative. Cache entries are retained only for online players,
 * and the separate balance/settings versions are merged independently so an out-of-order
 * notification cannot roll back either part of an account snapshot.</p>
 */
final class EconomyAccountCache {
    private final Economy feature;
    private final EconomySettings settings;
    private final EconomyRepository repository;
    private final EconomyIdentityResolver identities;
    private final EconomyMainThreadExecutor mainThread;
    private final BooleanSupplier closed;
    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Account>> refreshes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<List<Account>>> batchRefreshes = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    EconomyAccountCache(
            Economy feature,
            EconomySettings settings,
            EconomyRepository repository,
            EconomyIdentityResolver identities,
            EconomyMainThreadExecutor mainThread,
            BooleanSupplier closed
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.closed = Objects.requireNonNull(closed, "closed");
    }

    /** Starts initial and periodic refreshes for the players currently on this server. */
    void start() {
        refreshOnlinePlayers();
        BukkitTime period = BukkitTime.milliseconds(settings.cache().authoritativeRefreshInterval().toMillis());
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(this::refreshOnlinePlayers, period, period);
    }

    /** Begins a coalesced refresh after a player joins or becomes visible to Economy. */
    void preload(Player player) {
        if (player == null || closed.getAsBoolean()) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        onlinePlayers.add(playerUuid);
        identities.whenReady(playerUuid).whenComplete((resolved, failure) -> {
            if (failure != null || resolved.isEmpty() || closed.getAsBoolean()) {
                return;
            }
            Identity identity = resolved.get();
            CompletableFuture<List<Account>> refresh = batchRefreshes.computeIfAbsent(identity.playerUuid(), ignored -> {
                CompletableFuture<List<Account>> created = submit(() -> repository.balances(
                        identity,
                        settings.currencies().values()
                ));
                created.whenComplete((_, _) -> batchRefreshes.remove(identity.playerUuid(), created));
                return created;
            });
            refresh.thenAccept(result -> {
                if (!closed.getAsBoolean() && onlinePlayers.contains(identity.playerUuid())) {
                    result.forEach(this::cache);
                }
            }).exceptionally(error -> {
                feature.getLogger().warning("Could not refresh Economy accounts for " + playerName
                        + ": " + EconomyFailure.rootMessage(error));
                return null;
            });
        });
    }

    /** Removes all state associated with a player that left this server. */
    void evict(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        onlinePlayers.remove(playerUuid);
        accounts.entrySet().removeIf(entry -> entry.getValue().identity().playerUuid().equals(playerUuid));
        batchRefreshes.remove(playerUuid);
    }

    Optional<Account> get(UUID playerUuid, EconomySettings.Currency currency) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(key(playerUuid, currency)));
    }

    CompletableFuture<Account> load(Identity identity, EconomySettings.Currency currency) {
        return submit(() -> repository.balance(identity, currency)).thenApply(this::cache);
    }

    /** Applies an authenticated remote balance invalidation by reading the canonical database row. */
    void applyRemoteBalance(EconomyBalanceMessage message, EconomySettings.Currency currency) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(message.getPlayerUuid());
        } catch (RuntimeException ignored) {
            return;
        }
        String key = key(playerUuid, currency);
        mainThread.execute(() -> {
            Account current = accounts.get(key);
            if (current != null
                    && current.version() >= message.getBalanceVersion()
                    && current.settingsVersion() >= message.getSettingsVersion()) {
                return;
            }
            // Do not create cache entries for players that are absent on this server.
            if (Bukkit.getPlayer(playerUuid) == null && current == null) {
                return;
            }
            refreshAtLeast(
                    playerUuid,
                    message.getPlayerId(),
                    currency,
                    message.getBalanceVersion(),
                    message.getSettingsVersion()
            ).exceptionally(error -> {
                feature.getLogger().warning("Could not refresh remote Economy invalidation for " + playerUuid
                        + ": " + EconomyFailure.rootMessage(error));
                return null;
            });
        });
    }

    /** Refreshes a known canonical account, coalescing concurrent reads for the same account. */
    CompletableFuture<Account> refresh(Identity identity, EconomySettings.Currency currency) {
        String key = key(identity.playerUuid(), currency);
        return refreshes.computeIfAbsent(key, ignored -> {
            CompletableFuture<Account> refresh = submit(() -> repository.balance(identity, currency))
                    .thenApply(account -> onlinePlayers.contains(identity.playerUuid()) ? cache(account) : account);
            refresh.whenComplete((_, _) -> refreshes.remove(key, refresh));
            return refresh;
        });
    }

    /** Bypasses an existing coalesced read after a newer remote version was announced. */
    CompletableFuture<Account> refreshFresh(UUID playerUuid, long expectedPlayerId, EconomySettings.Currency currency) {
        return identities.resolveCanonical(playerUuid, expectedPlayerId)
                .thenCompose(identity -> submit(() -> repository.balance(identity, currency))
                        .thenApply(account -> onlinePlayers.contains(identity.playerUuid()) ? cache(account) : account))
                .toCompletableFuture();
    }

    Account cache(Account account) {
        if (account == null || closed.getAsBoolean() || !onlinePlayers.contains(account.identity().playerUuid())) {
            return account;
        }
        return accounts.merge(key(account.identity().playerUuid(), account.currencyId(), account.scopeKey()), account,
                EconomyAccountCache::merge);
    }

    void clear() {
        accounts.clear();
        refreshes.clear();
        batchRefreshes.clear();
        onlinePlayers.clear();
    }

    /** Merges independent optimistic versions from balance and player-account settings. */
    static Account merge(Account current, Account update) {
        Account balanceSource = update.version() >= current.version() ? update : current;
        Account settingsSource = update.settingsVersion() >= current.settingsVersion() ? update : current;
        return new Account(
                balanceSource.accountId(), balanceSource.identity(), balanceSource.currencyId(), balanceSource.scopeKey(),
                balanceSource.balance(), balanceSource.version(), settingsSource.settingsVersion(),
                settingsSource.paymentsEnabled(), settingsSource.status()
        );
    }

    private CompletableFuture<Account> refreshAtLeast(
            UUID playerUuid,
            long expectedPlayerId,
            EconomySettings.Currency currency,
            long minimumBalanceVersion,
            long minimumSettingsVersion
    ) {
        return identities.resolveCanonical(playerUuid, expectedPlayerId).thenCompose(identity ->
                refresh(identity, currency).thenCompose(account -> {
                    if (account.version() >= minimumBalanceVersion && account.settingsVersion() >= minimumSettingsVersion) {
                        return CompletableFuture.completedFuture(account);
                    }
                    // An earlier periodic read can finish after the remote commit. One fresh read
                    // establishes a post-invalidation snapshot without retrying indefinitely.
                    return refreshFresh(identity.playerUuid(), identity.playerId(), currency);
                })
        ).toCompletableFuture();
    }

    private void refreshOnlinePlayers() {
        if (!closed.getAsBoolean()) {
            Bukkit.getOnlinePlayers().forEach(this::preload);
        }
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> work) {
        if (closed.getAsBoolean()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Economy is closed"));
        }
        return feature.getLifecycleManager().getTaskManager().supplyAsync(work);
    }

    private static String key(UUID playerUuid, EconomySettings.Currency currency) {
        return key(playerUuid, currency.id(), currency.scope().key());
    }

    private static String key(UUID playerUuid, String currencyId, String scopeKey) {
        return playerUuid + "|" + currencyId + "|" + scopeKey;
    }
}
