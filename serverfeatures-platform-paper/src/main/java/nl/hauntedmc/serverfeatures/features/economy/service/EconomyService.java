package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyBalance;
import nl.hauntedmc.serverfeatures.api.economy.EconomyCurrency;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
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
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRejectedException;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public Economy facade.
 *
 * <p>This class deliberately contains only use-case orchestration and the public platform
 * surface. Identity authority, cache coherence, Bukkit thread dispatching, and transfer
 * notification delivery live in dedicated collaborators.</p>
 */
public final class EconomyService implements EconomyApi {
    private final Economy feature;
    private final EconomySettings settings;
    private final EconomyRepository repository;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final EconomyMainThreadExecutor mainThread;
    private final EconomyIdentityResolver identities;
    private final EconomyAccountCache cache;
    private final EconomyTransferNotifier transferNotifier;
    private volatile EconomyMessaging messaging;

    public EconomyService(Economy feature, EconomySettings settings, EconomyRepository repository) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.mainThread = new EconomyMainThreadExecutor(feature, closed::get);
        this.identities = new EconomyIdentityResolver(feature, closed::get);
        this.cache = new EconomyAccountCache(feature, settings, repository, identities, mainThread, closed::get);
        this.transferNotifier = new EconomyTransferNotifier(feature, repository, cache, mainThread, this::format);
    }

    /** Starts online-player cache maintenance. */
    public void start() {
        cache.start();
    }

    public void setMessaging(EconomyMessaging messaging) {
        this.messaging = messaging;
    }

    public EconomySettings settings() {
        return settings;
    }

    /** Preloads all configured currencies for a joining player. */
    public void preload(Player player) {
        cache.preload(player);
    }

    /** Evicts a departing player so the cache cannot grow with offline accounts. */
    public void evict(UUID playerUuid) {
        cache.evict(playerUuid);
    }

    @Override
    public CompletionStage<EconomyBalance> balance(EconomyAccountRef account) {
        return identities.resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return cache.load(identity, currency).thenApply(this::apiBalance);
        });
    }

    @Override
    public Optional<EconomyBalance> cachedBalance(EconomyAccountRef account) {
        if (account == null || account.playerUuid() == null) {
            return Optional.empty();
        }
        try {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return cache.get(account.playerUuid(), currency).map(this::apiBalance);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override public CompletionStage<EconomyResult> deposit(EconomyMutationRequest request) {
        return mutate(request, TransactionType.DEPOSIT, false);
    }
    @Override public CompletionStage<EconomyResult> withdraw(EconomyMutationRequest request) {
        return mutate(request, TransactionType.WITHDRAW, false);
    }
    @Override public CompletionStage<EconomyResult> setBalance(EconomyMutationRequest request) {
        return mutate(request, TransactionType.SET, false);
    }

    /** Executes a single-account mutation and publishes the resulting authoritative snapshots. */
    public CompletionStage<EconomyResult> mutate(EconomyMutationRequest request, TransactionType type, boolean bypassFreeze) {
        Objects.requireNonNull(request, "request");
        return identities.resolve(request.account()).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(request.account().currencyId(), request.account().scopeKey());
            return submit(() -> repository.mutate(type, identity, currency, request.amount(), request.source(),
                    request.idempotencyKey(), request.actorPlayerId(), request.actorName(), request.reason(),
                    request.metadata(), bypassFreeze)).thenApply(this::publishAndConvert);
        }).exceptionally(this::failureResult);
    }

    @Override
    public CompletionStage<EconomyResult> transfer(EconomyTransferRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<Identity> sender = identities.resolve(request.sender()).toCompletableFuture();
        CompletableFuture<Identity> recipient = identities.resolve(request.recipient()).toCompletableFuture();
        return sender.thenCombine(recipient, ResolvedTransfer::new).thenCompose(resolved -> {
            EconomySettings.Currency currency = requireCurrency(request.sender().currencyId(), request.sender().scopeKey());
            EconomySettings.Currency recipientCurrency = requireCurrency(request.recipient().currencyId(), request.recipient().scopeKey());
            if (!currency.id().equals(recipientCurrency.id()) || !currency.scope().key().equals(recipientCurrency.scope().key())) {
                return CompletableFuture.completedFuture(new EconomyResult(EconomyResultStatus.INVALID_AMOUNT, null, null, null,
                        "Transfers require the same currency and scope"));
            }
            return submit(() -> repository.transfer(resolved.sender(), resolved.recipient(), currency, request.amount(),
                    request.source(), request.idempotencyKey(), request.actorPlayerId(), request.actorName(), request.reason(),
                    request.metadata(), request.bypassPaymentsToggle(), false))
                    .thenApply(outcome -> publishTransferAndConvert(outcome, resolved.sender(), resolved.recipient(), currency, request.amount()));
        }).exceptionally(this::failureResult);
    }

    public CompletionStage<Identity> resolveIdentifier(String identifier) { return identities.resolveIdentifier(identifier); }

    public EconomyAccountRef account(Identity identity, String currencyId) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return new EconomyAccountRef(identity.playerId(), identity.playerUuid(), identity.playerName(), currency.id(), currency.scope().key());
    }

    public EconomyAccountRef account(Player player, String currencyId) {
        Identity identity = identities.active(player.getUniqueId()).orElseThrow(() -> new IllegalStateException("Player identity is not ready"));
        return account(identity, currencyId);
    }

    public Optional<Account> cachedAccount(UUID playerUuid, String currencyId) {
        return cache.get(playerUuid, requireCurrency(currencyId, null));
    }

    public CompletionStage<Account> accountState(EconomyAccountRef account) {
        return identities.resolve(account).thenCompose(identity -> cache.load(identity, requireCurrency(account.currencyId(), account.scopeKey())));
    }

    /** Changes recipient-payment consent; monetary state remains untouched. */
    public CompletionStage<Account> setPaymentsEnabled(EconomyAccountRef account, boolean enabled, Long actorPlayerId,
                                                        String actorName, String reason, String source) {
        return identities.resolve(account).thenCompose(identity -> submit(() -> repository.setPaymentsEnabled(identity,
                requireCurrency(account.currencyId(), account.scopeKey()), enabled, source, UUID.randomUUID().toString(),
                actorPlayerId, actorName, reason, Map.of())).thenApply(this::publishAccountMutation));
    }

    /** Freezes or unfreezes an account through the audited repository operation. */
    public CompletionStage<Account> setFrozen(EconomyAccountRef account, boolean frozen, Long actorPlayerId,
                                              String actorName, String reason) {
        return identities.resolve(account).thenCompose(identity -> submit(() -> repository.setFrozen(identity,
                requireCurrency(account.currencyId(), account.scopeKey()), frozen, actorPlayerId, actorName, reason,
                "admin-command", UUID.randomUUID().toString(), Map.of())).thenApply(this::publishAccountMutation));
    }

    public CompletionStage<HistoryPage> history(EconomyAccountRef account, int page, int pageSize) {
        return identities.resolve(account).thenCompose(identity -> submit(() -> repository.history(identity,
                requireCurrency(account.currencyId(), account.scopeKey()), page, pageSize)));
    }

    public CompletionStage<java.util.List<TopEntry>> top(String currencyId, int page, int pageSize) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        return submit(() -> repository.top(currency, Math.max(0, page - 1) * pageSize, pageSize));
    }
    public CompletionStage<VerificationReport> verify() { return submit(repository::verify); }

    @Override public Optional<EconomyCurrency> currency(String currencyId) {
        if (currencyId == null) return Optional.empty();
        return Optional.ofNullable(settings.currencies().get(currencyId.trim().toLowerCase(Locale.ROOT))).map(this::apiCurrency);
    }
    @Override public Collection<EconomyCurrency> currencies() { return settings.currencies().values().stream().map(this::apiCurrency).toList(); }

    @Override
    public String format(String currencyId, BigDecimal amount) {
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        BigDecimal normalized = amount.setScale(currency.display().fractionalDigits(), currency.balances().rounding());
        DecimalFormat formatter = new DecimalFormat();
        formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setGroupingUsed(currency.display().grouping());
        formatter.setMinimumFractionDigits(currency.display().fractionalDigits());
        formatter.setMaximumFractionDigits(currency.display().fractionalDigits());
        String noun = normalized.abs().compareTo(BigDecimal.ONE) == 0 ? currency.display().singular() : currency.display().plural();
        return currency.display().format().replace("{symbol}", currency.display().symbol()).replace("{amount}", formatter.format(normalized))
                .replace("{singular}", currency.display().singular()).replace("{plural}", noun);
    }

    // Vault exposes a synchronous SPI. These adapters are intentionally isolated from the async API above.
    public Optional<Identity> resolveSync(OfflinePlayer player) { return identities.resolveSync(player); }
    public Optional<Identity> resolveSync(String playerName) { return identities.resolveSync(playerName); }
    public Optional<Account> balanceSync(Identity identity, String currencyId) {
        return identity == null || closed.get()
                ? Optional.empty()
                : Optional.of(cache.cache(repository.balance(identity, requireCurrency(currencyId, null))));
    }
    public Optional<Account> balanceSync(OfflinePlayer player, String currencyId) { return resolveSync(player).flatMap(identity -> balanceSync(identity, currencyId)); }
    public MutationOutcome mutateSync(Identity identity, String currencyId, BigDecimal amount, TransactionType type, String source, String idempotencyKey) {
        if (closed.get()) {
            return new MutationOutcome(EconomyResultStatus.TEMPORARY_FAILURE, null, null, null,
                    "Economy service is shut down", null, null);
        }
        EconomySettings.Currency currency = requireCurrency(currencyId, null);
        if (identity == null) return new MutationOutcome(EconomyResultStatus.UNKNOWN_PLAYER, null, null, null, "Player identity is unavailable", null, null);
        MutationOutcome outcome = repository.mutate(type, identity, currency, amount, source, idempotencyKey, null, source,
                source + " economy operation", Map.of(), false);
        publish(outcome);
        return outcome;
    }
    public MutationOutcome mutateSync(OfflinePlayer player, String currencyId, BigDecimal amount, TransactionType type, String idempotencyKey) {
        return mutateSync(resolveSync(player).orElse(null), currencyId, amount, type, "vault", idempotencyKey);
    }
    public boolean hasAccountSync(OfflinePlayer player, String currencyId) { return resolveSync(player).map(identity -> hasAccountSync(identity, currencyId)).orElse(false); }
    public boolean hasAccountSync(Identity identity, String currencyId) {
        return identity != null && !closed.get()
                && repository.accountExists(identity, requireCurrency(currencyId, null));
    }

    /** Validates a balance invalidation before asking the cache to refresh from MySQL. */
    public void applyRemoteBalance(EconomyBalanceMessage message) {
        if (message == null || message.getSchemaVersion() != EconomyBalanceMessage.SCHEMA_VERSION || message.getPlayerId() <= 0L
                || message.getPlayerUuid() == null || message.getCurrencyId() == null || message.getScopeKey() == null
                || message.getBalanceVersion() < 0L || message.getSettingsVersion() < 0L) return;
        EconomySettings.Currency currency = settings.currencies().get(message.getCurrencyId());
        if (currency != null && currency.scope().key().equals(message.getScopeKey())) cache.applyRemoteBalance(message, currency);
    }

    /** Validates a transfer notification before the notifier verifies its committed receipt. */
    public void applyRemoteTransfer(EconomyTransferMessage message) {
        if (message == null || message.getSchemaVersion() != EconomyTransferMessage.SCHEMA_VERSION || message.getOperationId() == null
                || message.getRecipientPlayerId() <= 0L || message.getRecipientPlayerUuid() == null || message.getCurrencyId() == null
                || message.getScopeKey() == null) return;
        EconomySettings.Currency currency = settings.currencies().get(message.getCurrencyId());
        if (currency != null && currency.scope().key().equals(message.getScopeKey())) transferNotifier.applyRemote(message, currency);
    }

    public EconomySettings.Currency requireCurrency(String currencyId) { return requireCurrency(currencyId, null); }
    public EconomySettings.Currency primaryCurrency() { return requireCurrency(settings.vault().primaryCurrency(), null); }
    /** Exposes the primary-thread boundary to commands and integrations completing async work. */
    public void main(Runnable runnable) { mainThread.execute(runnable); }

    /**
     * Stops this feature-owned service and releases its in-memory state.
     *
     * <p>This is deliberately not {@link AutoCloseable}: the Economy feature owns one shared
     * instance for its whole enabled lifetime. Commands, listeners, placeholders, messaging, and
     * Vault receive a borrowed reference and must never close it with try-with-resources.</p>
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) { cache.clear(); transferNotifier.clear(); messaging = null; }
    }

    private EconomySettings.Currency requireCurrency(String currencyId, String requestedScope) {
        EconomySettings.Currency currency;
        try { currency = settings.requireCurrency(currencyId); }
        catch (IllegalArgumentException exception) { throw new UnknownCurrencyException(exception.getMessage(), exception); }
        if (requestedScope != null && !requestedScope.isBlank() && !currency.scope().key().equals(requestedScope.trim()))
            throw new UnknownCurrencyException("Currency " + currency.id() + " is not configured for scope " + requestedScope);
        return currency;
    }
    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> work) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Economy is closed"));
        return feature.getLifecycleManager().getTaskManager().supplyAsync(work);
    }
    private Account publishAccountMutation(MutationOutcome outcome) {
        if (outcome == null || !outcome.successful() || outcome.account() == null)
            throw new IllegalStateException(outcome == null || outcome.message().isBlank() ? "Economy account-setting operation failed" : outcome.message());
        publish(outcome); return outcome.account();
    }
    private EconomyResult publishAndConvert(MutationOutcome outcome) { publish(outcome); return result(outcome); }
    private EconomyResult publishTransferAndConvert(MutationOutcome outcome, Identity sender, Identity recipient, EconomySettings.Currency currency, BigDecimal requestedAmount) {
        publish(outcome);
        if (outcome != null && outcome.successful() && outcome.operationId() != null && outcome.counterpartBalance() != null) try {
            BigDecimal amount = requestedAmount.setScale(currency.display().fractionalDigits(), currency.balances().rounding());
            transferNotifier.notifyLocal(outcome.operationId(), sender, recipient, currency, amount);
            EconomyMessaging current = messaging;
            if (current != null) current.publishTransfer(outcome.operationId().toString(), recipient, currency.id(), currency.scope().key());
        } catch (RuntimeException failure) {
            feature.getLogger().warning("Could not fan out committed Economy transfer " + outcome.operationId() + ": " + EconomyFailure.rootMessage(failure));
        }
        return result(outcome);
    }
    private void publish(MutationOutcome outcome) {
        if (outcome == null || !outcome.successful()) return;
        String operationId = outcome.operationId() == null ? "" : outcome.operationId().toString();
        publishSnapshot(operationId, outcome.account()); publishSnapshot(operationId, outcome.counterpart());
    }
    private void publishSnapshot(String operationId, Account account) {
        if (account == null) return;
        try { cache.cache(account); EconomyMessaging current = messaging; if (current != null) current.publish(operationId, account); }
        catch (RuntimeException failure) { feature.getLogger().warning("Could not fan out committed Economy operation " + operationId + ": " + EconomyFailure.rootMessage(failure)); }
    }
    private EconomyResult failureResult(Throwable failure) {
        Throwable root = EconomyFailure.unwrap(failure);
        if (root instanceof EconomyRejectedException rejected) return new EconomyResult(rejected.status(), null, null, null, EconomyFailure.rootMessage(rejected));
        if (root instanceof UnknownPlayerException) return new EconomyResult(EconomyResultStatus.UNKNOWN_PLAYER, null, null, null, EconomyFailure.rootMessage(root));
        if (root instanceof UnknownCurrencyException) return new EconomyResult(EconomyResultStatus.UNKNOWN_CURRENCY, null, null, null, EconomyFailure.rootMessage(root));
        if (root instanceof IllegalArgumentException) return new EconomyResult(EconomyResultStatus.INVALID_AMOUNT, null, null, null, EconomyFailure.rootMessage(root));
        feature.getLogger().warning("Economy operation failed: " + EconomyFailure.rootMessage(root));
        return new EconomyResult(EconomyResultStatus.TEMPORARY_FAILURE, null, null, null, EconomyFailure.rootMessage(root));
    }
    private static EconomyResult result(MutationOutcome outcome) { return new EconomyResult(outcome.status(), outcome.operationId(), outcome.balance(), outcome.counterpartBalance(), outcome.message()); }
    private EconomyBalance apiBalance(Account account) { return new EconomyBalance(new EconomyAccountRef(account.identity().playerId(), account.identity().playerUuid(), account.identity().playerName(), account.currencyId(), account.scopeKey()), account.balance(), account.version()); }
    private EconomyCurrency apiCurrency(EconomySettings.Currency currency) { return new EconomyCurrency(currency.id(), currency.display().singular(), currency.display().plural(), currency.display().symbol(), currency.display().fractionalDigits(), currency.scope(), currency.balances().minimum(), currency.balances().maximum(), currency.commands().pay()); }
    /** Compatibility seam for cache merge tests; merging is owned by {@link EconomyAccountCache}. */
    static Account mergeAccount(Account current, Account update) { return EconomyAccountCache.merge(current, update); }
    private record ResolvedTransfer(Identity sender, Identity recipient) { }
}
