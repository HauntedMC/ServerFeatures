package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyPersistenceValues.accountId;
import static nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyPersistenceValues.snapshot;

/**
 * Stable persistence façade for Economy.
 *
 * <p>Public methods retain one repository entry point for callers while focused package-private
 * stores implement definition validation, provisioning, commands, policies, idempotency, ledger
 * writes, and read models. All mutation collaborators share the same ORM transaction.</p>
 */
public final class EconomyRepository {
    private final ORMContext orm;
    private final EconomyTransactionExecutor executor = new EconomyTransactionExecutor();
    private final EconomyAccountStore accountStore = new EconomyAccountStore();
    private final EconomyQueryStore queries = new EconomyQueryStore();
    private final EconomyPaymentPolicy paymentPolicy = new EconomyPaymentPolicy();
    private final EconomyDefinitionStore definitions = new EconomyDefinitionStore();
    private final EconomyIdempotencyStore idempotency = new EconomyIdempotencyStore();
    private final EconomyMutationStore mutations;

    /** Creates a repository backed by the supplied ORM context. */
    public EconomyRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
        this.mutations = new EconomyMutationStore(orm, executor, accountStore, paymentPolicy, idempotency);
    }

    /** Ensures this server's currency definitions match the network's immutable definitions. */
    public void validateDefinitions(EconomySettings settings) {
        executeWithRetry(() -> orm.runInTransaction(session -> {
            definitions.validate(session, settings);
            return null;
        }));
    }

    /** Returns the authoritative account snapshot, provisioning the account when absent. */
    public Account balance(Identity identity, EconomySettings.Currency currency) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyTransactionExecutor.Clock clock = new EconomyTransactionExecutor.Clock(session);
            EconomyBalanceEntity balance = accountStore.ensureAccount(session, identity, currency, clock, false);
            EconomyPlayerSettingsEntity settings = accountStore.ensureSettings(session, balance.getId(), currency, clock, false);
            return snapshot(balance, settings);
        }));
    }

    /** Loads all requested currency snapshots in one transaction for player preloading. */
    public List<Account> balances(
            Identity identity,
            Collection<EconomySettings.Currency> currencies
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currencies, "currencies");
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyTransactionExecutor.Clock clock = new EconomyTransactionExecutor.Clock(session);
            accountStore.ensurePlayerIdentity(session, identity, clock, false);
            List<EconomySettings.Currency> orderedCurrencies = currencies.stream()
                    .sorted(Comparator.comparing(EconomySettings.Currency::id))
                    .toList();
            List<String> accountIds = orderedCurrencies.stream()
                    .map(currency -> accountId(identity.playerId(), currency.id(), currency.scope().key()))
                    .toList();
            Map<String, EconomyBalanceEntity> balances = new LinkedHashMap<>();
            for (EconomyBalanceEntity balance : session.createSelectionQuery(
                            "from EconomyBalanceEntity where id in :ids",
                            EconomyBalanceEntity.class
                    )
                    .setParameter("ids", accountIds)
                    .getResultList()) {
                balances.put(balance.getId(), balance);
            }
            Map<String, EconomyPlayerSettingsEntity> settingsByAccount = new LinkedHashMap<>();
            for (EconomyPlayerSettingsEntity playerSettings : session.createSelectionQuery(
                            "from EconomyPlayerSettingsEntity where accountId in :ids",
                            EconomyPlayerSettingsEntity.class
                    )
                    .setParameter("ids", accountIds)
                    .getResultList()) {
                settingsByAccount.put(playerSettings.getAccountId(), playerSettings);
            }

            List<Account> accounts = new ArrayList<>(orderedCurrencies.size());
            for (EconomySettings.Currency currency : orderedCurrencies) {
                String accountId = accountId(identity.playerId(), currency.id(), currency.scope().key());
                EconomyBalanceEntity balance = balances.get(accountId);
                if (balance == null) {
                    balance = accountStore.ensureAccount(session, identity, currency, clock, false);
                } else {
                    accountStore.validateIdentity(balance, identity);
                }
                EconomyPlayerSettingsEntity playerSettings = settingsByAccount.get(accountId);
                if (playerSettings == null) {
                    playerSettings = accountStore.ensureSettings(session, accountId, currency, clock, false);
                }
                accounts.add(snapshot(balance, playerSettings));
            }
            return List.copyOf(accounts);
        }));
    }

    /** Checks for an existing account without provisioning one. */
    public boolean accountExists(Identity identity, EconomySettings.Currency currency) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currency, "currency");
        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity account = session.find(EconomyBalanceEntity.class, id);
            if (account == null) {
                return false;
            }
            accountStore.validateIdentity(account, identity);
            return true;
        }));
    }

    /** Executes an idempotent, audited single-account balance command. */
    public MutationOutcome mutate(
            TransactionType type,
            Identity identity,
            EconomySettings.Currency currency,
            BigDecimal rawAmount,
            String source,
            String idempotencyKey,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            boolean bypassFreeze
    ) {
        return mutations.mutate(type, identity, currency, rawAmount, source, idempotencyKey,
                actorPlayerId, actorName, reason, metadata, bypassFreeze);
    }

    /** Executes an atomic, policy-checked transfer with balanced sender/recipient journal rows. */
    public MutationOutcome transfer(
            Identity senderIdentity,
            Identity recipientIdentity,
            EconomySettings.Currency currency,
            BigDecimal rawAmount,
            String source,
            String idempotencyKey,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            boolean bypassPaymentsToggle,
            boolean bypassFreeze
    ) {
        return mutations.transfer(senderIdentity, recipientIdentity, currency, rawAmount, source,
                idempotencyKey, actorPlayerId, actorName, reason, metadata, bypassPaymentsToggle, bypassFreeze);
    }

    /** Reconstructs and verifies a committed transfer receipt from its journal. */
    public Optional<TransferReceipt> transferReceipt(UUID operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> queries.transferReceipt(session, operationId)));
    }

    /** Audits a change to incoming-payment consent without changing the balance. */
    public MutationOutcome setPaymentsEnabled(
            Identity identity,
            EconomySettings.Currency currency,
            boolean enabled,
            String source,
            String idempotencyKey,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata
    ) {
        return mutations.setPaymentsEnabled(identity, currency, enabled, source, idempotencyKey,
                actorPlayerId, actorName, reason, metadata);
    }

    /** Audits an account freeze-state change without changing the balance. */
    public MutationOutcome setFrozen(
            Identity identity,
            EconomySettings.Currency currency,
            boolean frozen,
            Long actorPlayerId,
            String actorName,
            String reason,
            String source,
            String idempotencyKey,
            Map<String, String> metadata
    ) {
        return mutations.setFrozen(identity, currency, frozen, actorPlayerId, actorName, reason,
                source, idempotencyKey, metadata);
    }

    /** Returns a newest-first page of account journal entries. */
    public HistoryPage history(
            Identity identity,
            EconomySettings.Currency currency,
            int page,
            int pageSize
    ) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("History page and page size must be positive");
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity account = accountStore.ensureAccount(session, identity, currency,
                    new EconomyTransactionExecutor.Clock(session), false);
            return queries.history(session, account, page, pageSize);
        }));
    }

    /** Returns an authoritative leaderboard page for one configured currency and scope. */
    public List<TopEntry> top(EconomySettings.Currency currency, int offset, int limit) {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("Leaderboard offset must be non-negative and limit positive");
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> queries.top(session, currency, offset, limit)));
    }

    /** Runs read-only ledger, identity, account, and definition integrity checks. */
    public VerificationReport verify() {
        return executeWithRetry(() -> orm.runInTransaction(queries::verify));
    }

    private <T> T executeWithRetry(Supplier<T> work) {
        return executor.execute(work);
    }

}
