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
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyBalanceMessage;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyMessaging;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native asynchronous Economy API and feature service. */
public final class EconomyService implements EconomyApi, AutoCloseable {
    private final Economy feature;
    private final EconomySettings settings;
    private final EconomyRepository repository;
    private final PlayerIdentityResolver identityResolver;
    private final ConcurrentHashMap<String, Account> cache = new ConcurrentHashMap<>();
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            preload(player);
        }
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
        identityResolver.whenReady(player.getUniqueId()).whenComplete((resolved, failure) -> {
            if (failure != null || resolved == null || resolved.isEmpty() || closed.get()) {
                return;
            }
            Identity identity = identity(resolved.get());
            for (EconomySettings.Currency currency : settings.currencies().values()) {
                submit(() -> repository.balance(identity, currency, now()))
                        .thenAccept(this::cache)
                        .exceptionally(error -> {
                            feature.getLogger().warning(
                                    "Could not preload " + currency.id() + " for " + player.getName()
                                            + ": " + rootMessage(error)
                            );
                            return null;
                        });
            }
        });
    }

    public void evict(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        cache.entrySet().removeIf(entry -> entry.getValue().identity().playerUuid().equals(playerUuid));
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
                    .thenApply(this::publishAndConvert);
        }).exceptionally(this::failureResult);
    }


    public CompletionStage<Identity> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player identifier must not be blank"));
        }
        return identityResolver.findByIdentifier(identifier.trim()).thenApply(optional -> optional
                .map(EconomyService::identity)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + identifier)));
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

    public CompletionStage<Account> setPaymentsEnabled(
            EconomyAccountRef account,
            boolean enabled
    ) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return submit(() -> repository.setPaymentsEnabled(identity, currency, enabled, now()))
                    .thenApply(this::cache);
        });
    }

    public CompletionStage<Account> setFrozen(
            EconomyAccountRef account,
            boolean frozen,
            Long actorPlayerId,
            String reason
    ) {
        return resolve(account).thenCompose(identity -> {
            EconomySettings.Currency currency = requireCurrency(account.currencyId(), account.scopeKey());
            return submit(() -> repository.setFrozen(identity, currency, frozen, actorPlayerId, reason, now()))
                    .thenApply(this::cache);
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
        return submit(() -> repository.verify(settings));
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
        Optional<PlayerIdentity> active = identityResolver.findActiveByUuid(player.getUniqueId());
        if (active.isPresent()) {
            return active.map(EconomyService::identity);
        }
        return repository.findIdentityByUuid(player.getUniqueId());
    }

    public Optional<Identity> resolveSync(String playerName) {
        if (playerName == null || playerName.isBlank() || closed.get()) {
            return Optional.empty();
        }
        return identityResolver.findActiveByUsername(playerName).map(EconomyService::identity)
                .or(() -> repository.findIdentityByName(playerName));
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
                || message.getBalance() == null
                || message.getCurrencyId() == null
                || message.getScopeKey() == null) {
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
        String key = cacheKey(uuid, message.getCurrencyId(), message.getScopeKey());
        cache.compute(key, (ignored, current) -> {
            if (current != null && current.version() >= message.getVersion()) {
                return current;
            }
            return new Account(
                    current == null ? "remote:" + message.getPlayerId() : current.accountId(),
                    new Identity(message.getPlayerId(), uuid, message.getPlayerName()),
                    message.getCurrencyId(),
                    message.getScopeKey(),
                    message.getBalance(),
                    message.getVersion(),
                    current == null ? currency.payments().defaultEnabled() : current.paymentsEnabled(),
                    current == null ? AccountStatus.ACTIVE : current.status()
            );
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
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(runnable);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cache.clear();
    }

    private EconomySettings.Currency requireCurrency(String currencyId, String requestedScope) {
        EconomySettings.Currency currency = settings.requireCurrency(currencyId);
        if (requestedScope != null && !requestedScope.isBlank()
                && !currency.scope().key().equals(requestedScope.trim())) {
            throw new IllegalArgumentException("Currency " + currency.id() + " is not configured for scope " + requestedScope);
        }
        return currency;
    }

    private CompletionStage<Identity> resolve(EconomyAccountRef account) {
        Objects.requireNonNull(account, "account");
        return identityResolver.findByUuid(account.playerUuid()).thenApply(optional -> {
            Identity canonical = optional.map(EconomyService::identity)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + account.playerUuid()));
            if (account.playerId() != null
                    && account.playerId() > 0L
                    && account.playerId() != canonical.playerId()) {
                throw new IllegalArgumentException("Player ID does not match UUID: " + account.playerUuid());
            }
            return canonical;
        });
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
        cache.merge(
                cacheKey(account.identity().playerUuid(), account.currencyId(), account.scopeKey()),
                account,
                (current, update) -> update.version() >= current.version() ? update : current
        );
        return account;
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
        if (outcome.account() != null) {
            cache(outcome.account());
            EconomyMessaging current = messaging;
            if (current != null) {
                current.publish(operationId, outcome.account());
            }
        }
        if (outcome.counterpart() != null) {
            cache(outcome.counterpart());
            EconomyMessaging current = messaging;
            if (current != null) {
                current.publish(operationId, outcome.counterpart());
            }
        }
    }

    private EconomyResult failureResult(Throwable failure) {
        Throwable root = unwrap(failure);
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

    private record ResolvedTransfer(Identity sender, Identity recipient) {
    }
}
