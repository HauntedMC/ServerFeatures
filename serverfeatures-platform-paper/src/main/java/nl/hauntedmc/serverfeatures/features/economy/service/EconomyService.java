package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyBalance;
import nl.hauntedmc.serverfeatures.api.economy.EconomyCurrency;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyBalanceMessage;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyMessaging;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyTransferMessage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRejectedException;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native asynchronous Economy API and feature service. */
public final class EconomyService implements EconomyApi, AutoCloseable {
    private static final long NOTIFICATION_DEDUP_MILLIS = 10 * 60 * 1_000L;
    private static final int MAX_NOTIFICATION_DEDUP_ENTRIES = 10_000;
    private static final long SYNCHRONOUS_IDENTITY_TIMEOUT_MILLIS = 1_000L;

    private final Economy feature;
    private final EconomySettings settings;
    private final EconomyRepository repository;
    private final PlayerIdentityResolver identityResolver;
    private final ConcurrentHashMap<String, Account> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Account>> refreshes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<List<Account>>> batchRefreshes = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> notifiedTransfers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile EconomyMessaging messaging;

    public EconomyService(Economy feature, EconomySettings settings, EconomyRepository repository) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identityResolver = new PlayerIdentityResolver(
                feature.getPlugin().getDataRegistry().orElseThrow(() -> new IllegalStateException(
                        "Economy requires DataRegistry"
                ))
        );
    }

    public void start() {
        refreshOnlinePlayers();
        BukkitTime refreshPeriod = BukkitTime.milliseconds(
                settings.cache().authoritativeRefreshInterval().toMillis()
        );
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::refreshOnlinePlayers,
                refreshPeriod,
                refreshPeriod
        );
    }

    public void setMessaging(EconomyMessaging messaging) {
        this.messaging = messaging;
    }

    public EconomySettings settings() {
        return settings;
    }

    public void preload(Player player) {
        if (player == null || closed.get()) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        onlinePlayers.add(playerUuid);
        identityResolver.whenReady(playerUuid).whenComplete((resolved, failure) -> {
            if (failure != null || resolved == null || resolved.isEmpty() || closed.get()) {
                return;
            }
            Identity identity = identity(resolved.get());
            CompletableFuture<List<Account>> refresh = batchRefreshes.computeIfAbsent(
                    identity.playerUuid(),
                    ignored -> {
                        CompletableFuture<List<Account>> created = submit(() -> repository.balances(
                                identity,
                                settings.currencies().values(),
                                now()
                        ));
                        created.whenComplete((accounts, error) -> batchRefreshes.remove(
                                identity.playerUuid(),
                                created
                        ));
                        return created;
                    }
            );
            refresh.thenAccept(accounts -> {
                if (!closed.get() && onlinePlayers.contains(identity.playerUuid())) {
                    accounts.forEach(this::cache);
                }
            }).exceptionally(error -> {
                        feature.getLogger().warning(
                                "Could not refresh Economy accounts for " + playerName
                                        + ": " + rootMessage(error)
                        );
                        return null;
                    });
        });
    }

    public void evict(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        onlinePlayers.remove(playerUuid);
        cache.entrySet().removeIf(entry -> entry.getValue().identity().playerUuid().equals(playerUuid));
        batchRefreshes.remove(playerUuid);
    }

    @Override
    public CompletionStage<EconomyBalance> balance(EconomyAccountRef account) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return submit(() -> repository.balance(identity, currency, now()))
                    .thenApply(this::cache)
                    .thenApply(this::apiBalance);
        });
    }

    @Override
    public Optional<EconomyBalance> cachedBalance(EconomyAccountRef account) {
        if (account == null || account.playerUuid() == null) {
            return Optional.empty();
        }
        EconomySettings.Currency currency;
        try {
            currency = requireCurrency(account.currencyId(), account.scopeKey());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        Account cached = cache.get(cacheKey(account.playerUuid(), currency.id(), currency.scope().key()));
        return Optional.ofNullable(cached).map(this::apiBalance);
    }

    @Override
    public CompletionStage<EconomyResult> deposit(EconomyMutationRequest request) {
        return mutate(request, TransactionType.DEPOSIT, false);
    }

    @Override
    public CompletionStage<EconomyResult> withdraw(EconomyMutationRequest request) {
        return mutate(request, TransactionType.WITHDRAW, false);
    }

    @Override
    public CompletionStage<EconomyResult> setBalance(EconomyMutationRequest request) {
        return mutate(request, TransactionType.SET, false);
    }

    public CompletionStage<EconomyResult> mutate(
            EconomyMutationRequest request,
            TransactionType type,
            boolean bypassFreeze
    ) {
        Objects.requireNonNull(request, "request");
        return resolve(request.account()).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(
                    request.account().currencyId(),
                    request.account().scopeKey()
            );
            TransactionType journalType = requestedJournalType(type, request.metadata());
            return submit(() -> repository.mutate(
                            type,
                            journalType,
                            identity,
                            currency,
                            request.amount(),
                            request.source(),
                            request.idempotencyKey(),
                            request.actorPlayerId(),
                            request.actorName(),
                            request.reason(),
                            request.metadata(),
                            bypassFreeze,
                            now()
                    ))
                    .thenApply(outcome -> publishAndConvert(outcome));
        }).exceptionally(this::failureResult);
    }

    @Override
    public CompletionStage<EconomyResult> transfer(EconomyTransferRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<Identity> sender = resolve(request.sender()).toCompletableFuture();
        CompletableFuture<Identity> recipient = resolve(request.recipient()).toCompletableFuture();
        return sender.thenCombine(recipient, ResolvedTransfer::new).thenCompose(resolved -> {
            EconomySettings.Currency currency = requireCurrency(
                    request.sender().currencyId(),
                    request.sender().scopeKey()
            );
            EconomySettings.Currency recipientCurrency = requireCurrency(
                    request.recipient().currencyId(),
                    request.recipient().scopeKey()
            );
            if (!currency.id().equals(recipientCurrency.id())
                    || !currency.scope().key().equals(recipientCurrency.scope().key())) {
                return CompletableFuture.completedFuture(new EconomyResult(
                        EconomyResultStatus.INVALID_AMOUNT,
                        null,
                        null,
                        null,
                        "Transfers require the same currency and scope"
                ));
            }
            return submit(() -> repository.transfer(
                            resolved.sender(),
                            resolved.recipient(),
                            currency,
                            request.amount(),
                            request.source(),
                            request.idempotencyKey(),
                            request.actorPlayerId(),
                            request.actorName(),
                            request.reason(),
                            request.metadata(),
                            request.bypassPaymentsToggle(),
                            false,
                            now()
                    ))
                    .thenApply(outcome -> publishTransferAndConvert(
                            outcome,
                            resolved.sender(),
                            resolved.recipient(),
                            currency,
                            request.amount()
                    ));
        }).exceptionally(this::failureResult);
    }

    public CompletionStage<Identity> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player identifier must not be blank"));
        }
        return identityResolver.findByIdentifier(identifier.trim()).thenApply(optional -> optional
                .map(EconomyService::identity)
                .orElseThrow(() -> new UnknownPlayerException("Unknown player: " + identifier)));
    }

    public EconomyAccountRef account(Identity identity, String currencyId) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return new EconomyAccountRef(
                identity.playerId(),
                identity.playerUuid(),
                identity.playerName(),
                currency.id(),
                currency.scope().key()
        );
    }

    public EconomyAccountRef account(Player player, String currencyId) {
        Optional<PlayerIdentity> active = identityResolver.findActiveByUuid(player.getUniqueId());
        if (active.isEmpty()) {
            throw new IllegalStateException("Player identity is not ready");
        }
        return account(identity(active.get()), currencyId);
    }

    public Optional<Account> cachedAccount(UUID playerUuid, String currencyId) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return Optional.ofNullable(cache.get(cacheKey(playerUuid, currency.id(), currency.scope().key())));
    }

    public CompletionStage<Account> accountState(EconomyAccountRef account) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return submit(() -> repository.balance(identity, currency, now())).thenApply(this::cache);
        });
    }

    public CompletionStage<Account> setPaymentsEnabled(
            EconomyAccountRef account,
            boolean enabled,
            Long actorPlayerId,
            String actorName,
            String reason,
            String source
    ) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            String idempotencyKey = UUID.randomUUID().toString();
            return submit(() -> repository.setPaymentsEnabled(
                            identity,
                            currency,
                            enabled,
                            source,
                            idempotencyKey,
                            actorPlayerId,
                            actorName,
                            reason,
                            Map.of(),
                            now()
                    ))
                    .thenApply(this::publishAccountMutation);
        });
    }

    public CompletionStage<Account> setFrozen(
            EconomyAccountRef account,
            boolean frozen,
            Long actorPlayerId,
            String actorName,
            String reason
    ) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            String idempotencyKey = UUID.randomUUID().toString();
            return submit(() -> repository.setFrozen(
                            identity,
                            currency,
                            frozen,
                            actorPlayerId,
                            actorName,
                            reason,
                            "admin-command",
                            idempotencyKey,
                            Map.of(),
                            now()
                    ))
                    .thenApply(this::publishAccountMutation);
        });
    }

    public CompletionStage<HistoryPage> history(EconomyAccountRef account, int page, int pageSize) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return submit(() -> repository.history(identity, currency, page, pageSize, now()));
        });
    }

    public CompletionStage<List<TopEntry>> top(String currencyId, int page, int pageSize) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return submit(() -> repository.top(currency, Math.max(0, page - 1) * pageSize, pageSize));
    }

    public CompletionStage<VerificationReport> verify() {
        return submit(repository::verify);
    }

    @Override
    public Optional<EconomyCurrency> currency(String currencyId) {
        if (currencyId == null) {
            return Optional.empty();
        }
        EconomySettings.Currency currency = settings.currencies().get(currencyId.trim().toLowerCase(Locale.ROOT));
        return Optional.ofNullable(currency).map(this::apiCurrency);
    }

    @Override
    public Collection<EconomyCurrency> currencies() {
        return settings.currencies().values().stream().map(this::apiCurrency).toList();
    }

    @Override
    public String format(String currencyId, BigDecimal amount) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        BigDecimal normalized = amount.setScale(
                currency.display().fractionalDigits(),
                currency.balances().rounding()
        );
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat formatter = new DecimalFormat();
        formatter.setDecimalFormatSymbols(symbols);
        formatter.setGroupingUsed(currency.display().grouping());
        formatter.setMinimumFractionDigits(currency.display().fractionalDigits());
        formatter.setMaximumFractionDigits(currency.display().fractionalDigits());
        String number = formatter.format(normalized);
        String noun = normalized.abs().compareTo(BigDecimal.ONE) == 0
                ? currency.display().singular()
                : currency.display().plural();
        return currency.display().format()
                .replace("{symbol}", currency.display().symbol())
                .replace("{amount}", number)
                .replace("{singular}", currency.display().singular())
                .replace("{plural}", noun);
    }

    public Optional<Identity> resolveSync(OfflinePlayer player) {
        if (player == null || closed.get()) {
            return Optional.empty();
        }
        UUID playerUuid = player.getUniqueId();
        Optional<PlayerIdentity> active = identityResolver.findActiveByUuid(playerUuid);
        if (active.isPresent()) {
            return active.map(EconomyService::identity);
        }
        // DataRegistry remains canonical even for an already existing Economy account.
        // A registry outage must fail closed rather than authorizing a stale identity mapping.
        return awaitIdentity(identityResolver.findByUuid(playerUuid), playerUuid.toString());
    }

    public Optional<Identity> resolveSync(String playerName) {
        if (playerName == null || playerName.isBlank() || closed.get()) {
            return Optional.empty();
        }
        String normalized = playerName.trim();
        Optional<PlayerIdentity> active = identityResolver.findActiveByUsername(normalized);
        if (active.isPresent()) {
            return active.map(EconomyService::identity);
        }
        // Player names are mutable and may be reassigned. Never trust the denormalized
        // name stored on an Economy account for a monetary lookup.
        return awaitIdentity(identityResolver.findByUsername(normalized), normalized);
    }

    public Optional<Account> balanceSync(Identity identity, String currencyId) {
        if (identity == null) {
            return Optional.empty();
        }
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return Optional.of(cache(repository.balance(identity, currency, now())));
    }

    public Optional<Account> balanceSync(OfflinePlayer player, String currencyId) {
        return resolveSync(player).flatMap(identity -> balanceSync(identity, currencyId));
    }

    public boolean hasBalanceSync(Identity identity, String currencyId, BigDecimal amount) {
        if (identity == null || amount == null || amount.signum() < 0) {
            return false;
        }
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        BigDecimal normalized = amount.setScale(
                currency.display().fractionalDigits(),
                currency.balances().rounding()
        );
        if (amount.signum() > 0 && normalized.signum() == 0) {
            return false;
        }
        return balanceSync(identity, currencyId)
                .map(Account::balance)
                .map(balance -> balance.subtract(normalized).compareTo(currency.balances().minimum()) >= 0)
                .orElse(false);
    }

    public MutationOutcome mutateSync(
            Identity identity,
            String currencyId,
            BigDecimal amount,
            TransactionType type,
            String source,
            String idempotencyKey
    ) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        if (identity == null) {
            return new MutationOutcome(
                    EconomyResultStatus.UNKNOWN_PLAYER, null, null, null,
                    "Player identity is unavailable", null, null
            );
        }
        MutationOutcome outcome = repository.mutate(
                type, type, identity, currency, amount, source, idempotencyKey, null,
                source, source + " economy operation", Map.of(), false, now()
        );
        publish(outcome);
        return outcome;
    }

    public MutationOutcome mutateSync(
            OfflinePlayer player,
            String currencyId,
            BigDecimal amount,
            TransactionType type,
            String idempotencyKey
    ) {
        return mutateSync(
                resolveSync(player).orElse(null),
                currencyId,
                amount,
                type,
                "vault",
                idempotencyKey
        );
    }

    public boolean hasAccountSync(OfflinePlayer player, String currencyId) {
        return resolveSync(player).map(identity -> hasAccountSync(identity, currencyId)).orElse(false);
    }

    public boolean hasAccountSync(Identity identity, String currencyId) {
        if (identity == null) {
            return false;
        }
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return repository.accountExists(identity, currency);
    }

    public void applyRemoteBalance(EconomyBalanceMessage message) {
        if (message == null
                || message.getSchemaVersion() != EconomyBalanceMessage.SCHEMA_VERSION
                || message.getPlayerId() <= 0L
                || message.getPlayerUuid() == null
                || message.getCurrencyId() == null
                || message.getScopeKey() == null
                || message.getBalanceVersion() < 0L
                || message.getSettingsVersion() < 0L) {
            return;
        }
        EconomySettings.Currency currency = settings.currencies().get(message.getCurrencyId());
        if (currency == null || !currency.scope().key().equals(message.getScopeKey())) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(message.getPlayerUuid());
        } catch (RuntimeException exception) {
            return;
        }
        String key = cacheKey(uuid, currency.id(), currency.scope().key());
        main(() -> {
            Account current = cache.get(key);
            if (current != null
                    && current.version() >= message.getBalanceVersion()
                    && current.settingsVersion() >= message.getSettingsVersion()) {
                return;
            }
            if (Bukkit.getPlayer(uuid) == null && current == null) {
                return;
            }
            refreshCanonicalAtLeast(
                    uuid,
                    message.getPlayerId(),
                    currency,
                    message.getBalanceVersion(),
                    message.getSettingsVersion()
            ).exceptionally(error -> {
                feature.getLogger().warning(
                        "Could not refresh remote Economy invalidation for " + uuid + ": " + rootMessage(error)
                );
                return null;
            });
        });
    }

    public void applyRemoteTransfer(EconomyTransferMessage message) {
        if (message == null
                || message.getSchemaVersion() != EconomyTransferMessage.SCHEMA_VERSION
                || message.getOperationId() == null
                || message.getRecipientPlayerId() <= 0L
                || message.getRecipientPlayerUuid() == null
                || message.getCurrencyId() == null
                || message.getScopeKey() == null) {
            return;
        }
        EconomySettings.Currency currency = settings.currencies().get(message.getCurrencyId());
        if (currency == null || !currency.scope().key().equals(message.getScopeKey())) {
            return;
        }
        UUID operationId;
        UUID recipientUuid;
        try {
            operationId = UUID.fromString(message.getOperationId());
            recipientUuid = UUID.fromString(message.getRecipientPlayerUuid());
        } catch (RuntimeException exception) {
            return;
        }
        main(() -> {
            if (Bukkit.getPlayer(recipientUuid) == null) {
                return;
            }
            submit(() -> repository.transferReceipt(operationId)).thenCompose(optional -> {
                TransferReceipt receipt = optional.orElseThrow(() -> new IllegalArgumentException(
                        "Unknown Economy transfer: " + operationId
                ));
                if (receipt.recipient().playerId() != message.getRecipientPlayerId()
                        || !receipt.recipient().playerUuid().equals(recipientUuid)
                        || !receipt.currencyId().equals(currency.id())
                        || !receipt.scopeKey().equals(currency.scope().key())) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException(
                            "Economy transfer notification does not match the committed transaction"
                    ));
                }
                return refreshCanonicalFresh(recipientUuid, message.getRecipientPlayerId(), currency)
                        .thenApply(account -> new VerifiedTransfer(receipt, account));
            }).whenComplete((verified, failure) -> main(() -> {
                Player recipient = Bukkit.getPlayer(recipientUuid);
                if (recipient == null) {
                    return;
                }
                if (failure != null) {
                    feature.getLogger().warning(
                            "Could not verify Economy transfer for " + recipientUuid
                                    + ": " + rootMessage(failure)
                    );
                    return;
                }
                if (!markTransferNotification(operationId)) {
                    return;
                }
                feature.send(recipient, "economy.pay.received", Map.of(
                        "player", verified.receipt().sender().playerName(),
                        "amount", format(currency.id(), verified.receipt().amount()),
                        "balance", format(currency.id(), verified.account().balance())
                ));
            }));
        });
    }

    public EconomySettings.Currency requireCurrency(String currencyId) {
        return requireCurrency(currencyId, null);
    }

    public EconomySettings.Currency primaryCurrency() {
        return requireCurrency(settings.vault().primaryCurrency(), null);
    }

    public void main(Runnable runnable) {
        if (closed.get()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                if (!closed.get()) {
                    runnable.run();
                }
            });
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cache.clear();
        refreshes.clear();
        batchRefreshes.clear();
        onlinePlayers.clear();
        notifiedTransfers.clear();
    }

    private EconomySettings.Currency requireCurrency(String currencyId, String requestedScope) {
        EconomySettings.Currency currency;
        try {
            currency = settings.requireCurrency(currencyId);
        } catch (IllegalArgumentException exception) {
            throw new UnknownCurrencyException(exception.getMessage(), exception);
        }
        if (requestedScope != null && !requestedScope.isBlank()
                && !currency.scope().key().equals(requestedScope.trim())) {
            throw new UnknownCurrencyException(
                    "Currency " + currency.id() + " is not configured for scope " + requestedScope
            );
        }
        return currency;
    }

    private CompletionStage<Identity> resolve(EconomyAccountRef account) {
        Objects.requireNonNull(account, "account");
        return identityResolver.findByUuid(account.playerUuid()).thenApply(optional -> optional
                .map(EconomyService::identity)
                .map(resolved -> {
                    if (account.playerId() != null
                            && account.playerId() > 0L
                            && account.playerId() != resolved.playerId()) {
                        throw new IllegalArgumentException(
                                "Player ID does not match UUID: " + account.playerUuid()
                        );
                    }
                    return resolved;
                })
                .orElseThrow(() -> new UnknownPlayerException("Unknown player: " + account.playerUuid())));
    }

    private Optional<Identity> awaitIdentity(
            CompletionStage<Optional<PlayerIdentity>> lookup,
            String identifier
    ) {
        try {
            return lookup.toCompletableFuture()
                    .get(SYNCHRONOUS_IDENTITY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .map(EconomyService::identity);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving player identity " + identifier, exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Could not resolve player identity " + identifier, unwrap(exception));
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out resolving player identity " + identifier, exception);
        }
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> work) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Economy is closed"));
        }
        return feature.getLifecycleManager().getTaskManager().supplyAsync(work);
    }

    private Account cache(Account account) {
        if (account == null || closed.get()) {
            return account;
        }
        // The cache only serves online-player placeholders and UI. Keeping offline accounts
        // indefinitely would turn normal Vault/admin traffic into an unbounded memory cache.
        if (!onlinePlayers.contains(account.identity().playerUuid())) {
            return account;
        }
        return cache.merge(
                cacheKey(account.identity().playerUuid(), account.currencyId(), account.scopeKey()),
                account,
                EconomyService::mergeAccount
        );
    }

    private void refreshOnlinePlayers() {
        if (closed.get()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            preload(player);
        }
    }

    private CompletableFuture<Account> refreshCanonicalAtLeast(
            UUID playerUuid,
            long expectedPlayerId,
            EconomySettings.Currency currency,
            long minimumBalanceVersion,
            long minimumSettingsVersion
    ) {
        return resolveCanonical(playerUuid, expectedPlayerId).thenCompose(identity ->
                refreshAccount(identity, currency).thenCompose(account -> {
                    if (account.version() >= minimumBalanceVersion
                            && account.settingsVersion() >= minimumSettingsVersion) {
                        return CompletableFuture.completedFuture(account);
                    }
                    // A periodic read may have started before the remote commit and can therefore
                    // complete with an older snapshot. Force one post-invalidation read instead of
                    // reusing that stale in-flight future.
                    return refreshAccountFresh(identity, currency);
                })
        ).toCompletableFuture();
    }

    private CompletableFuture<Account> refreshCanonicalFresh(
            UUID playerUuid,
            long expectedPlayerId,
            EconomySettings.Currency currency
    ) {
        return resolveCanonical(playerUuid, expectedPlayerId)
                .thenCompose(identity -> refreshAccountFresh(identity, currency))
                .toCompletableFuture();
    }

    private CompletionStage<Identity> resolveCanonical(UUID playerUuid, long expectedPlayerId) {
        return identityResolver.findByUuid(playerUuid).thenCompose(resolved -> {
            if (resolved == null || resolved.isEmpty() || closed.get()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown player identity: " + playerUuid
                ));
            }
            Identity identity = identity(resolved.get());
            if (identity.playerId() != expectedPlayerId) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Player identity mismatch for " + playerUuid
                ));
            }
            return CompletableFuture.completedFuture(identity);
        });
    }

    private CompletableFuture<Account> refreshAccount(Identity identity, EconomySettings.Currency currency) {
        String key = cacheKey(identity.playerUuid(), currency.id(), currency.scope().key());
        return refreshes.computeIfAbsent(key, ignored -> {
            CompletableFuture<Account> refresh = submit(() -> repository.balance(identity, currency, now()))
                    .thenApply(account -> onlinePlayers.contains(identity.playerUuid()) ? cache(account) : account);
            refresh.whenComplete((account, failure) -> refreshes.remove(key, refresh));
            return refresh;
        });
    }

    private CompletableFuture<Account> refreshAccountFresh(
            Identity identity,
            EconomySettings.Currency currency
    ) {
        return submit(() -> repository.balance(identity, currency, now()))
                .thenApply(account -> onlinePlayers.contains(identity.playerUuid()) ? cache(account) : account);
    }

    private Account publishAccountMutation(MutationOutcome outcome) {
        if (outcome == null || !outcome.successful() || outcome.account() == null) {
            String message = outcome == null || outcome.message().isBlank()
                    ? "Economy account-setting operation failed"
                    : outcome.message();
            throw new IllegalStateException(message);
        }
        publish(outcome);
        return outcome.account();
    }

    private EconomyResult publishTransferAndConvert(
            MutationOutcome outcome,
            Identity sender,
            Identity recipient,
            EconomySettings.Currency currency,
            BigDecimal requestedAmount
    ) {
        publish(outcome);
        if (outcome != null
                && outcome.successful()
                && outcome.operationId() != null
                && outcome.counterpartBalance() != null) {
            BigDecimal amount = requestedAmount.setScale(
                    currency.display().fractionalDigits(),
                    currency.balances().rounding()
            );
            try {
                notifyLocalTransfer(
                        outcome.operationId(),
                        sender,
                        recipient,
                        currency,
                        amount
                );
                EconomyMessaging current = messaging;
                if (current != null) {
                    current.publishTransfer(
                            outcome.operationId().toString(),
                            recipient,
                            currency.id(),
                            currency.scope().key()
                    );
                }
            } catch (RuntimeException failure) {
                feature.getLogger().warning(
                        "Could not fan out committed Economy transfer " + outcome.operationId()
                                + ": " + rootMessage(failure)
                );
            }
        }
        return result(outcome);
    }

    private void notifyLocalTransfer(
            UUID operationId,
            Identity sender,
            Identity recipient,
            EconomySettings.Currency currency,
            BigDecimal amount
    ) {
        main(() -> {
            if (Bukkit.getPlayer(recipient.playerUuid()) == null) {
                return;
            }
            refreshAccount(recipient, currency).whenComplete((account, failure) -> main(() -> {
                Player online = Bukkit.getPlayer(recipient.playerUuid());
                if (online == null) {
                    return;
                }
                if (failure != null) {
                    feature.getLogger().warning(
                            "Could not refresh local Economy transfer for " + recipient.playerUuid()
                                    + ": " + rootMessage(failure)
                    );
                    return;
                }
                if (!markTransferNotification(operationId)) {
                    return;
                }
                feature.send(online, "economy.pay.received", Map.of(
                        "player", sender.playerName(),
                        "amount", format(currency.id(), amount),
                        "balance", format(currency.id(), account.balance())
                ));
            }));
        });
    }

    private boolean markTransferNotification(UUID operationId) {
        long currentTime = now();
        if (notifiedTransfers.putIfAbsent(operationId, currentTime) != null) {
            return false;
        }
        if (notifiedTransfers.size() > MAX_NOTIFICATION_DEDUP_ENTRIES) {
            long cutoff = currentTime - NOTIFICATION_DEDUP_MILLIS;
            notifiedTransfers.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            int excess = notifiedTransfers.size() - MAX_NOTIFICATION_DEDUP_ENTRIES;
            if (excess > 0) {
                notifiedTransfers.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(excess)
                        .map(Map.Entry::getKey)
                        .toList()
                        .forEach(notifiedTransfers::remove);
            }
        }
        return true;
    }

    static Account mergeAccount(Account current, Account update) {
        Account balanceSource = update.version() >= current.version() ? update : current;
        Account settingsSource = update.settingsVersion() >= current.settingsVersion() ? update : current;
        return new Account(
                balanceSource.accountId(),
                balanceSource.identity(),
                balanceSource.currencyId(),
                balanceSource.scopeKey(),
                balanceSource.balance(),
                balanceSource.version(),
                settingsSource.settingsVersion(),
                settingsSource.paymentsEnabled(),
                settingsSource.status()
        );
    }

    private EconomyResult publishAndConvert(MutationOutcome outcome) {
        publish(outcome);
        return result(outcome);
    }

    private void publish(MutationOutcome outcome) {
        if (outcome == null || !outcome.successful()) {
            return;
        }
        String operationId = outcome.operationId() == null ? "" : outcome.operationId().toString();
        publishAccountSnapshot(operationId, outcome.account());
        publishAccountSnapshot(operationId, outcome.counterpart());
    }

    private void publishAccountSnapshot(String operationId, Account account) {
        if (account == null) {
            return;
        }
        try {
            cache(account);
            EconomyMessaging current = messaging;
            if (current != null) {
                current.publish(operationId, account);
            }
        } catch (RuntimeException failure) {
            // The database commit already succeeded. Cache or Redis fan-out must never
            // convert a committed economy operation into an apparent caller failure.
            feature.getLogger().warning(
                    "Could not fan out committed Economy operation " + operationId + ": " + rootMessage(failure)
            );
        }
    }

    private EconomyResult failureResult(Throwable failure) {
        Throwable root = unwrap(failure);
        if (root instanceof EconomyRejectedException rejected) {
            return new EconomyResult(rejected.status(), null, null, null, rootMessage(rejected));
        }
        if (root instanceof UnknownPlayerException) {
            return new EconomyResult(EconomyResultStatus.UNKNOWN_PLAYER, null, null, null, rootMessage(root));
        }
        if (root instanceof UnknownCurrencyException) {
            return new EconomyResult(EconomyResultStatus.UNKNOWN_CURRENCY, null, null, null, rootMessage(root));
        }
        if (root instanceof IllegalArgumentException) {
            return new EconomyResult(EconomyResultStatus.INVALID_AMOUNT, null, null, null, rootMessage(root));
        }
        feature.getLogger().warning("Economy operation failed: " + rootMessage(root));
        return new EconomyResult(EconomyResultStatus.TEMPORARY_FAILURE, null, null, null, rootMessage(root));
    }

    private static EconomyResult result(MutationOutcome outcome) {
        return new EconomyResult(
                outcome.status(),
                outcome.operationId(),
                outcome.balance(),
                outcome.counterpartBalance(),
                outcome.message()
        );
    }

    private EconomyBalance apiBalance(Account account) {
        return new EconomyBalance(
                new EconomyAccountRef(
                        account.identity().playerId(),
                        account.identity().playerUuid(),
                        account.identity().playerName(),
                        account.currencyId(),
                        account.scopeKey()
                ),
                account.balance(),
                account.version()
        );
    }

    private EconomyCurrency apiCurrency(EconomySettings.Currency currency) {
        return new EconomyCurrency(
                currency.id(),
                currency.display().singular(),
                currency.display().plural(),
                currency.display().symbol(),
                currency.display().fractionalDigits(),
                currency.scope(),
                currency.balances().minimum(),
                currency.balances().maximum(),
                currency.commands().pay()
        );
    }

    private static Identity identity(PlayerIdentity identity) {
        return new Identity(identity.playerId(), identity.uuid(), identity.username());
    }

    private static String cacheKey(UUID playerUuid, String currencyId, String scopeKey) {
        return playerUuid + "|" + currencyId + "|" + scopeKey;
    }

    static TransactionType requestedJournalType(
            TransactionType operationType,
            Map<String, String> metadata
    ) {
        if (metadata == null) {
            return operationType;
        }
        String requested = metadata.get("transaction_type");
        if (requested == null || requested.isBlank()) {
            return operationType;
        }
        try {
            TransactionType candidate = TransactionType.valueOf(requested.trim().toUpperCase(Locale.ROOT));
            return sameMutationDirection(operationType, candidate) ? candidate : operationType;
        } catch (IllegalArgumentException ignored) {
            return operationType;
        }
    }

    private static boolean sameMutationDirection(TransactionType left, TransactionType right) {
        return switch (left) {
            case DEPOSIT, ADMIN_ADD, LOTTERY_PAYOUT, LOTTERY_REFUND, VAULT_DEPOSIT -> switch (right) {
                case DEPOSIT, ADMIN_ADD, LOTTERY_PAYOUT, LOTTERY_REFUND, VAULT_DEPOSIT -> true;
                default -> false;
            };
            case WITHDRAW, ADMIN_REMOVE, LOTTERY_PURCHASE, LOTTERY_DONATION, VAULT_WITHDRAW -> switch (right) {
                case WITHDRAW, ADMIN_REMOVE, LOTTERY_PURCHASE, LOTTERY_DONATION, VAULT_WITHDRAW -> true;
                default -> false;
            };
            case SET, ADMIN_SET -> right == TransactionType.SET || right == TransactionType.ADMIN_SET;
            case TRANSFER -> right == TransactionType.TRANSFER;
            case ACCOUNT_CREATED -> right == TransactionType.ACCOUNT_CREATED;
            case PAYMENTS_ENABLED, PAYMENTS_DISABLED, ACCOUNT_FROZEN, ACCOUNT_UNFROZEN -> left == right;
        };
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = unwrap(throwable);
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record VerifiedTransfer(TransferReceipt receipt, Account account) {
    }

    private record ResolvedTransfer(Identity sender, Identity recipient) {
    }

    private static final class UnknownPlayerException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private UnknownPlayerException(String message) {
            super(message);
        }
    }

    private static final class UnknownCurrencyException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private UnknownCurrencyException(String message) {
            super(message);
        }

        private UnknownCurrencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
