package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.DiscoveredCurrencyDefinition;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.WorkflowOutcome;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final EconomyWorkflowStore workflows = new EconomyWorkflowStore();
    private final EconomyMutationStore mutations;

    /** Creates a repository backed by the supplied ORM context. */
    public EconomyRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
        this.mutations = new EconomyMutationStore(orm, executor, accountStore, paymentPolicy, idempotency, workflows);
    }

    /**
     * Validates currencies independently so one incompatible definition cannot disable all
     * unrelated currencies on a server.
     */
    public DefinitionValidation validateDefinitions(EconomySettings settings) {
        Map<String, EconomySettings.Currency> active = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        for (EconomySettings.Currency currency : settings.currencies().values().stream()
                .sorted(Comparator.comparing(EconomySettings.Currency::id)).toList()) {
            try {
                executeWithRetry(() -> orm.runInTransaction(session -> {
                    definitions.validate(session, settings, currency);
                    return null;
                }));
                active.put(currency.id(), currency);
            } catch (RuntimeException exception) {
                EconomyDefinitionException definitionFailure = definitionFailure(exception);
                if (definitionFailure == null) {
                    throw exception;
                }
                rejected.put(currency.id(), definitionFailure.getMessage());
            }
        }
        return new DefinitionValidation(Map.copyOf(active), Map.copyOf(rejected));
    }

    /**
     * ORM transaction implementations may wrap an application exception when rolling back.
     * Preserve per-currency startup isolation in that case, without treating unrelated database
     * failures as a bad currency definition.
     */
    private static EconomyDefinitionException definitionFailure(Throwable failure) {
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        for (Throwable current = failure; current != null && visited.put(current, Boolean.TRUE) == null;
             current = current.getCause()) {
            if (current instanceof EconomyDefinitionException definitionException) {
                return definitionException;
            }
        }
        return null;
    }

    /** Discovers canonical global/group definitions without reading or provisioning player accounts. */
    public List<DiscoveredCurrencyDefinition> sharedDefinitions(String networkKey) {
        Objects.requireNonNull(networkKey, "networkKey");
        return executeWithRetry(() -> orm.runInTransaction(session -> definitions.discoverShared(session, networkKey)));
    }

    /** Finds one importable or legacy shared definition by its stable currency ID and scope key. */
    public Optional<DiscoveredCurrencyDefinition> sharedDefinition(String networkKey, String currencyId, String scopeKey) {
        Objects.requireNonNull(networkKey, "networkKey");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(scopeKey, "scopeKey");
        return executeWithRetry(() -> orm.runInTransaction(session ->
                definitions.sharedDefinition(session, networkKey, currencyId, scopeKey)));
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

    /** Charges an account and makes a fulfilment event durable with the resulting journal transaction. */
    public WorkflowOutcome chargeAndDispatch(EconomyWorkflowRequest request, Identity identity,
                                             EconomySettings.Currency currency) {
        Objects.requireNonNull(request, "request");
        return mutations.chargeAndDispatch(request, identity, currency);
    }

    /** Finds the durable state for one business workflow identity. */
    public Optional<WorkflowStatus> workflow(EconomyWorkflowRef reference) {
        Objects.requireNonNull(reference, "reference");
        return executeWithRetry(() -> orm.runInTransaction(session -> workflows.find(session, reference)
                .map(snapshot -> new WorkflowStatus(snapshot.eventId(), snapshot.operationId(), snapshot.state(),
                        snapshot.attempts(), snapshot.lastError()))));
    }

    /** Leases ready workflow events without holding locks while their external handlers run. */
    public List<WorkflowClaim> claimWorkflows(Set<String> eventTypes, String owner, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Workflow claim limit must be between 1 and 100");
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            long now = EconomyTransactionExecutor.databaseNow(session);
            return workflows.claim(session, eventTypes, owner, now, limit).stream()
                    .map(claim -> new WorkflowClaim(claim.eventId(), claim.owner(), claim.event())).toList();
        }));
    }

    public void acknowledgeWorkflow(String eventId, String owner) {
        executeWithRetry(() -> orm.runInTransaction(session -> {
            workflows.acknowledge(session, eventId, owner, EconomyTransactionExecutor.databaseNow(session));
            return null;
        }));
    }

    public void releaseWorkflow(String eventId, String owner, String failure) {
        executeWithRetry(() -> orm.runInTransaction(session -> {
            workflows.release(session, eventId, owner, failure, EconomyTransactionExecutor.databaseNow(session));
            return null;
        }));
    }

    /** One leased outbox delivery; locks are released before its handler is invoked. */
    public record WorkflowClaim(String eventId, String owner,
                                nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowEvent event) { }

    /** Durable workflow status exposed without leaking the mutable outbox entity. */
    public record WorkflowStatus(java.util.UUID eventId, java.util.UUID operationId,
                                 nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowState state,
                                 int attempts, String lastError) { }

    /** Result of startup definition validation, keyed by normalized currency ID. */
    public record DefinitionValidation(
            Map<String, EconomySettings.Currency> activeCurrencies,
            Map<String, String> rejectedCurrencies
    ) { }

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

    /**
     * Deletes every account projection and journal row for a currency in one logical network,
     * while retaining its canonical definition.
     *
     * <p>This is intentionally not exposed through {@code EconomyApi}. Callers must provide the
     * maintenance gate and an explicit command confirmation before invoking it.</p>
     */
    public EconomyMaintenanceResult clearCurrencyData(String networkKey, String currencyId) {
        return purgeCurrency(networkKey, currencyId, false);
    }

    /** Removes a currency's durable data, definitions, and family marker in one transaction. */
    public EconomyMaintenanceResult removeCurrency(String networkKey, String currencyId) {
        return purgeCurrency(networkKey, currencyId, true);
    }

    /** Replaces the stored definition with the supplied current configuration after a full purge. */
    public EconomyMaintenanceResult redefineCurrency(EconomySettings configuredSettings,
                                                      EconomySettings.Currency currency) {
        Objects.requireNonNull(configuredSettings, "configuredSettings");
        Objects.requireNonNull(currency, "currency");
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyMaintenanceResult result = purgeCurrency(session, configuredSettings.networkKey(), currency.id(), true);
            definitions.validate(session, configuredSettings, currency);
            return result;
        }));
    }

    private EconomyMaintenanceResult purgeCurrency(String networkKey, String currencyId, boolean removeDefinitions) {
        Objects.requireNonNull(networkKey, "networkKey");
        Objects.requireNonNull(currencyId, "currencyId");
        return executeWithRetry(() -> orm.runInTransaction(session ->
                purgeCurrency(session, networkKey, currencyId, removeDefinitions)));
    }

    /**
     * Deletes children first because Economy intentionally has no database foreign keys: the
     * journal must remain independently verifiable during normal operation. Scope keys are
     * discovered before deletion and filtered in Java, avoiding SQL LIKE wildcard mistakes in
     * valid network keys such as {@code build_1}.
     */
    private EconomyMaintenanceResult purgeCurrency(org.hibernate.Session session, String networkKey,
                                                   String currencyId, boolean removeDefinitions) {
        List<String> scopeKeys = currencyScopeKeys(session, networkKey, currencyId);
        if (scopeKeys.isEmpty()) {
            if (removeDefinitions) {
                session.createMutationQuery("delete EconomyCurrencyFamilyEntity where id = :id")
                        .setParameter("id", networkKey + ":" + currencyId).executeUpdate();
            }
            return new EconomyMaintenanceResult(0, 0, 0, 0, 0, 0, 0);
        }
        int settings = accountMutation(session, "delete EconomyPlayerSettingsEntity where accountId like :accountPattern",
                currencyId, scopeKeys);
        int dailyUsage = accountMutation(session, "delete EconomyDailyUsageEntity where accountId like :accountPattern",
                currencyId, scopeKeys);
        int transactionEntries = mutation(session, "delete EconomyTransactionEntryEntity where transactionId in "
                + "(select id from EconomyTransactionEntity where currencyId = :currencyId and scopeKey in :scopeKeys)",
                currencyId, scopeKeys);
        transactionEntries += accountMutation(session, "delete EconomyTransactionEntryEntity where accountId like :accountPattern",
                currencyId, scopeKeys);
        int workflows = mutation(session, "delete EconomyWorkflowEntity where currencyId = :currencyId and scopeKey in :scopeKeys",
                currencyId, scopeKeys);
        int transactions = mutation(session, "delete EconomyTransactionEntity where currencyId = :currencyId and scopeKey in :scopeKeys",
                currencyId, scopeKeys);
        int balances = mutation(session, "delete EconomyBalanceEntity where currencyId = :currencyId and scopeKey in :scopeKeys",
                currencyId, scopeKeys);
        int definitionsRemoved = 0;
        if (removeDefinitions) {
            definitionsRemoved = mutation(session, "delete EconomyCurrencyDefinitionEntity where currencyId = :currencyId and scopeKey in :scopeKeys",
                    currencyId, scopeKeys);
            session.createMutationQuery("delete EconomyCurrencyFamilyEntity where id = :id")
                    .setParameter("id", networkKey + ":" + currencyId).executeUpdate();
        }
        return new EconomyMaintenanceResult(balances, settings, transactions, transactionEntries, dailyUsage,
                workflows, definitionsRemoved);
    }

    private static int mutation(org.hibernate.Session session, String hql, String currencyId, List<String> scopeKeys) {
        return session.createMutationQuery(hql).setParameter("currencyId", currencyId)
                .setParameter("scopeKeys", scopeKeys).executeUpdate();
    }

    /** Also removes orphaned account-linked rows without touching a same-named foreign network. */
    private static int accountMutation(org.hibernate.Session session, String hql, String currencyId, List<String> scopeKeys) {
        int removed = 0;
        for (String scopeKey : scopeKeys) {
            String accountSuffix = ":" + currencyId + ":" + EconomyPersistenceValues.hash(scopeKey);
            removed += session.createMutationQuery(hql).setParameter("accountPattern", "%" + accountSuffix).executeUpdate();
        }
        return removed;
    }

    private static List<String> currencyScopeKeys(org.hibernate.Session session, String networkKey, String currencyId) {
        Set<String> candidates = new java.util.LinkedHashSet<>();
        candidates.addAll(scopeKeys(session, "EconomyBalanceEntity", currencyId));
        candidates.addAll(scopeKeys(session, "EconomyTransactionEntity", currencyId));
        candidates.addAll(scopeKeys(session, "EconomyWorkflowEntity", currencyId));
        candidates.addAll(scopeKeys(session, "EconomyCurrencyDefinitionEntity", currencyId));
        String global = networkKey + "/global";
        String serverPrefix = networkKey + "/server/";
        String groupPrefix = networkKey + "/group/";
        return candidates.stream().filter(scope -> scope.equals(global) || scope.startsWith(serverPrefix)
                || scope.startsWith(groupPrefix)).sorted().toList();
    }

    private static List<String> scopeKeys(org.hibernate.Session session, String entity, String currencyId) {
        return session.createSelectionQuery("select distinct scopeKey from " + entity + " where currencyId = :currencyId",
                        String.class)
                .setParameter("currencyId", currencyId).getResultList();
    }

    private <T> T executeWithRetry(Supplier<T> work) {
        return executor.execute(work);
    }

}
