package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerIdentityEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/** Transactional MySQL repository. All balance mutations are committed atomically with their audit rows. */
public final class EconomyRepository {
    private static final int MAX_RETRIES = 3;
    private static final int DATABASE_SCALE = 8;
    private static final String DATABASE_TIME_QUERY =
            "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";
    private static final Gson GSON = new Gson();

    private final ORMContext orm;

    public EconomyRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public void validateDefinitions(EconomySettings settings) {
        executeWithRetry(() -> orm.runInTransaction(session -> {
            long now = databaseNow(session);
            List<EconomySettings.Currency> currencies = settings.currencies().values().stream()
                    .sorted(Comparator.comparing(EconomySettings.Currency::id))
                    .toList();
            for (EconomySettings.Currency currency : currencies) {
                validateCurrencyFamily(session, settings.networkKey(), currency, now);
                String id = definitionId(currency.id(), currency.scope().key());
                String hash = definitionHash(currency);
                EconomyCurrencyDefinitionEntity entity = session.find(
                        EconomyCurrencyDefinitionEntity.class,
                        id,
                        LockModeType.PESSIMISTIC_WRITE
                );
                if (entity == null) {
                    entity = new EconomyCurrencyDefinitionEntity();
                    entity.setId(id);
                    entity.setCurrencyId(currency.id());
                    entity.setScopeKey(currency.scope().key());
                    entity.setScopeType(currency.scope().type().name());
                    entity.setFractionalDigits(currency.display().fractionalDigits());
                    entity.setStartingBalance(databaseAmount(currency.balances().starting()));
                    entity.setMinimumBalance(databaseAmount(currency.balances().minimum()));
                    entity.setMaximumBalance(databaseAmount(currency.balances().maximum()));
                    entity.setAllowNegative(currency.balances().allowNegative());
                    entity.setDefinitionHash(hash);
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    session.persist(entity);
                    continue;
                }
                if (!hash.equals(entity.getDefinitionHash())) {
                    throw new IllegalStateException(
                            "Currency definition mismatch for " + currency.id() + " in scope " + currency.scope().key()
                    );
                }
                entity.setUpdatedAt(now);
            }
            return null;
        }));
    }


    private static void validateCurrencyFamily(
            Session session,
            String networkKey,
            EconomySettings.Currency currency,
            long now
    ) {
        String id = networkKey + ":" + currency.id();
        String globalScope = currency.scope().type() == nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType.GLOBAL
                ? currency.scope().key()
                : null;
        String familyHash = hash(String.join("|",
                networkKey,
                currency.id(),
                currency.scope().type().name(),
                Integer.toString(currency.display().fractionalDigits()),
                globalScope == null ? "" : globalScope
        ));
        EconomyCurrencyFamilyEntity family = session.find(
                EconomyCurrencyFamilyEntity.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (family == null) {
            family = new EconomyCurrencyFamilyEntity();
            family.setId(id);
            family.setNetworkKey(networkKey);
            family.setCurrencyId(currency.id());
            family.setScopeType(currency.scope().type().name());
            family.setFractionalDigits(currency.display().fractionalDigits());
            family.setGlobalScopeKey(globalScope);
            family.setFamilyHash(familyHash);
            family.setCreatedAt(now);
            family.setUpdatedAt(now);
            session.persist(family);
            return;
        }
        if (!familyHash.equals(family.getFamilyHash())) {
            throw new IllegalStateException(
                    "Currency family mismatch for " + currency.id() + " in network " + networkKey
                            + ": scope type, precision, or global scope differs between servers"
            );
        }
        family.setUpdatedAt(now);
    }


    public Account balance(Identity identity, EconomySettings.Currency currency) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            TransactionClock clock = new TransactionClock(session);
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, clock, false);
            EconomyPlayerSettingsEntity settings = ensureSettings(session, balance.getId(), currency, clock, false);
            return snapshot(balance, settings);
        }));
    }

    public List<Account> balances(
            Identity identity,
            Collection<EconomySettings.Currency> currencies
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currencies, "currencies");
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            TransactionClock clock = new TransactionClock(session);
            List<EconomySettings.Currency> orderedCurrencies = currencies.stream()
                    .sorted(Comparator.comparing(EconomySettings.Currency::id))
                    .toList();
            ensurePlayerIdentity(session, identity, clock, false);
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
                    balance = createAccount(session, accountId, identity, currency, clock);
                } else {
                    validateAccountIdentity(balance, identity);
                }
                EconomyPlayerSettingsEntity playerSettings = settingsByAccount.get(accountId);
                if (playerSettings == null) {
                    playerSettings = createSettings(session, accountId, currency, clock);
                }
                accounts.add(snapshot(balance, playerSettings));
            }
            return List.copyOf(accounts);
        }));
    }

    public boolean accountExists(Identity identity, EconomySettings.Currency currency) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currency, "currency");
        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity account = session.find(EconomyBalanceEntity.class, id);
            if (account == null) {
                return false;
            }
            validateAccountIdentity(account, identity);
            return true;
        }));
    }


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
        BigDecimal amount = normalizeMutationAmount(type, rawAmount, currency);
        String requestFingerprint = mutationFingerprint(
                type,
                type,
                identity,
                currency,
                amount,
                actorPlayerId,
                actorName,
                reason,
                metadata,
                bypassFreeze
        );
        try {
            return executeWithRetry(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = replay(session, source, idempotencyKey, requestFingerprint);
                if (replay != null) {
                    return replay;
                }
                TransactionClock provisioningClock = new TransactionClock(session);
                EconomyBalanceEntity balance = ensureAccount(session, identity, currency, provisioningClock, true);
                EconomyPlayerSettingsEntity playerSettings = ensureSettings(
                        session,
                        balance.getId(),
                        currency,
                        provisioningClock,
                        true
                );
                long transactionNow = databaseNow(session);
                requireActive(playerSettings, bypassFreeze);

                BigDecimal before = balance.getBalance();
                BigDecimal after = switch (type) {
                    case DEPOSIT -> before.add(amount);
                    case WITHDRAW -> before.subtract(amount);
                    case SET -> amount;
                    case TRANSFER -> throw new IllegalArgumentException("Use transfer() for transfers");
                    case ACCOUNT_CREATED -> throw new IllegalArgumentException("Account creation is internal");
                    case PAYMENTS_ENABLED, PAYMENTS_DISABLED, ACCOUNT_FROZEN, ACCOUNT_UNFROZEN ->
                            throw new IllegalArgumentException("Use the account-setting operation");
                };
                validateBalance(after, currency);
                balance.setBalance(databaseAmount(after));
                balance.setPlayerName(trim(identity.playerName(), 32));
                balance.setPlayerUuid(identity.playerUuid().toString());
                balance.setUpdatedAt(transactionNow);

                EconomyTransactionEntity transaction = transaction(
                        type,
                        currency,
                        source,
                        idempotencyKey,
                        requestFingerprint,
                        actorPlayerId,
                        actorName,
                        reason,
                        metadata,
                        transactionNow
                );
                session.persist(transaction);
                session.flush();
                persistEntry(
                        session,
                        transaction.getId(),
                        balance,
                        "TARGET",
                        after.subtract(before),
                        before,
                        after
                );
                session.flush();
                return outcome(
                        EconomyResultStatus.SUCCESS,
                        transaction.getOperationId(),
                        after,
                        null,
                        "",
                        snapshot(balance, playerSettings),
                        null
                );
            }));
        } catch (EconomyRejectedException rejected) {
            return outcome(rejected.status(), null, null, null, rejected.getMessage(), null, null);
        }
    }

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
        BigDecimal amount = normalizePositive(rawAmount, currency);
        if (senderIdentity.playerId() == recipientIdentity.playerId()) {
            return outcome(EconomyResultStatus.INVALID_AMOUNT, null, null, null,
                    "Sender and recipient must differ", null, null);
        }
        if (amount.compareTo(currency.payments().minimum()) < 0) {
            return outcome(EconomyResultStatus.LIMIT_EXCEEDED, null, null, null,
                    "Amount is below the minimum payment", null, null);
        }
        if (currency.payments().maximum().signum() > 0
                && amount.compareTo(currency.payments().maximum()) > 0) {
            return outcome(EconomyResultStatus.LIMIT_EXCEEDED, null, null, null,
                    "Amount exceeds the maximum payment", null, null);
        }
        String requestFingerprint = transferFingerprint(
                senderIdentity,
                recipientIdentity,
                currency,
                amount,
                actorPlayerId,
                actorName,
                reason,
                metadata,
                bypassPaymentsToggle,
                bypassFreeze
        );

        try {
            return executeWithRetry(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = replay(session, source, idempotencyKey, requestFingerprint);
                if (replay != null) {
                    return replay;
                }

                List<Identity> identities = new ArrayList<>(List.of(senderIdentity, recipientIdentity));
                identities.sort(Comparator.comparingLong(Identity::playerId));
                TransactionClock provisioningClock = new TransactionClock(session);
                Map<Long, EconomyBalanceEntity> locked = new LinkedHashMap<>();
                Map<Long, EconomyPlayerSettingsEntity> settings = new LinkedHashMap<>();
                for (Identity identity : identities) {
                    EconomyBalanceEntity account = ensureAccount(
                            session, identity, currency, provisioningClock, true
                    );
                    locked.put(identity.playerId(), account);
                    settings.put(identity.playerId(), ensureSettings(
                            session, account.getId(), currency, provisioningClock, true
                    ));
                }

                EconomyBalanceEntity sender = locked.get(senderIdentity.playerId());
                EconomyBalanceEntity recipient = locked.get(recipientIdentity.playerId());
                EconomyPlayerSettingsEntity senderSettings = settings.get(senderIdentity.playerId());
                EconomyPlayerSettingsEntity recipientSettings = settings.get(recipientIdentity.playerId());
                long transactionNow = databaseNow(session);
                requireActive(senderSettings, bypassFreeze);
                requireActive(recipientSettings, bypassFreeze);
                if (!bypassPaymentsToggle && !recipientSettings.isPaymentsEnabled()) {
                    throw new EconomyRejectedException(
                            EconomyResultStatus.PAYMENTS_DISABLED,
                            "Recipient has disabled incoming payments"
                    );
                }

                BigDecimal senderBefore = sender.getBalance();
                BigDecimal recipientBefore = recipient.getBalance();
                BigDecimal senderAfter = senderBefore.subtract(amount);
                BigDecimal recipientAfter = recipientBefore.add(amount);
                validateBalance(senderAfter, currency);
                validateBalance(recipientAfter, currency);
                enforcePaymentCooldown(senderSettings, currency, transactionNow);
                applyDailyLimits(session, sender, recipient, currency, amount, transactionNow);

                sender.setBalance(databaseAmount(senderAfter));
                sender.setPlayerName(trim(senderIdentity.playerName(), 32));
                sender.setUpdatedAt(transactionNow);
                recipient.setBalance(databaseAmount(recipientAfter));
                recipient.setPlayerName(trim(recipientIdentity.playerName(), 32));
                recipient.setUpdatedAt(transactionNow);

                EconomyTransactionEntity transaction = transaction(
                        TransactionType.TRANSFER,
                        currency,
                        source,
                        idempotencyKey,
                        requestFingerprint,
                        actorPlayerId,
                        actorName,
                        reason,
                        metadata,
                        transactionNow
                );
                session.persist(transaction);
                session.flush();
                persistEntry(session, transaction.getId(), sender, "SENDER", amount.negate(), senderBefore, senderAfter);
                persistEntry(session, transaction.getId(), recipient, "RECIPIENT", amount, recipientBefore, recipientAfter);
                session.flush();
                return outcome(
                        EconomyResultStatus.SUCCESS,
                        transaction.getOperationId(),
                        senderAfter,
                        recipientAfter,
                        "",
                        snapshot(sender, senderSettings),
                        snapshot(recipient, recipientSettings)
                );
            }));
        } catch (EconomyRejectedException rejected) {
            return outcome(rejected.status(), null, null, null, rejected.getMessage(), null, null);
        }
    }

    public Optional<TransferReceipt> transferReceipt(UUID operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyTransactionEntity transaction = session.createSelectionQuery(
                            "from EconomyTransactionEntity where operationId = :operationId",
                            EconomyTransactionEntity.class
                    )
                    .setParameter("operationId", operationId.toString())
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (transaction == null || !TransactionType.TRANSFER.name().equals(transaction.getTransactionType())) {
                return Optional.empty();
            }
            List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                            "from EconomyTransactionEntryEntity where transactionId = :transactionId order by id asc",
                            EconomyTransactionEntryEntity.class
                    )
                    .setParameter("transactionId", transaction.getId())
                    .getResultList();
            EconomyTransactionEntryEntity senderEntry = entries.stream()
                    .filter(entry -> "SENDER".equals(entry.getEntryRole()))
                    .findFirst()
                    .orElse(null);
            EconomyTransactionEntryEntity recipientEntry = entries.stream()
                    .filter(entry -> "RECIPIENT".equals(entry.getEntryRole()))
                    .findFirst()
                    .orElse(null);
            if (entries.size() != 2
                    || senderEntry == null
                    || recipientEntry == null
                    || senderEntry.getAccountId().equals(recipientEntry.getAccountId())
                    || recipientEntry.getDelta().signum() <= 0
                    || senderEntry.getDelta().negate().compareTo(recipientEntry.getDelta()) != 0
                    || senderEntry.getBalanceBefore().add(senderEntry.getDelta())
                            .compareTo(senderEntry.getBalanceAfter()) != 0
                    || recipientEntry.getBalanceBefore().add(recipientEntry.getDelta())
                            .compareTo(recipientEntry.getBalanceAfter()) != 0) {
                throw new IllegalStateException(
                        "Economy transfer " + operationId + " has invalid journal entries"
                );
            }
            EconomyBalanceEntity sender = session.find(EconomyBalanceEntity.class, senderEntry.getAccountId());
            EconomyBalanceEntity recipient = session.find(EconomyBalanceEntity.class, recipientEntry.getAccountId());
            if (sender == null
                    || recipient == null
                    || sender.getPlayerId() != senderEntry.getPlayerId()
                    || recipient.getPlayerId() != recipientEntry.getPlayerId()
                    || !transaction.getCurrencyId().equals(sender.getCurrencyId())
                    || !transaction.getCurrencyId().equals(recipient.getCurrencyId())
                    || !transaction.getScopeKey().equals(sender.getScopeKey())
                    || !transaction.getScopeKey().equals(recipient.getScopeKey())) {
                throw new IllegalStateException(
                        "Economy transfer " + operationId + " references an inconsistent account"
                );
            }
            return Optional.of(new TransferReceipt(
                    operationId,
                    identity(sender),
                    identity(recipient),
                    transaction.getCurrencyId(),
                    transaction.getScopeKey(),
                    recipientEntry.getDelta(),
                    recipientEntry.getBalanceAfter()
            ));
        }));
    }

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
        TransactionType type = enabled ? TransactionType.PAYMENTS_ENABLED : TransactionType.PAYMENTS_DISABLED;
        String requestFingerprint = accountSettingFingerprint(
                type, identity, currency, actorPlayerId, actorName, reason, metadata
        );
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            MutationOutcome replay = replay(session, source, idempotencyKey, requestFingerprint);
            if (replay != null) {
                return replay;
            }
            TransactionClock provisioningClock = new TransactionClock(session);
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, provisioningClock, true);
            EconomyPlayerSettingsEntity settings = ensureSettings(
                    session, balance.getId(), currency, provisioningClock, true
            );
            long transactionNow = databaseNow(session);
            BigDecimal unchanged = balance.getBalance();
            settings.setPaymentsEnabled(enabled);
            settings.setUpdatedAt(transactionNow);
            EconomyTransactionEntity transaction = transaction(
                    type,
                    currency,
                    source,
                    idempotencyKey,
                    requestFingerprint,
                    actorPlayerId,
                    actorName,
                    reason,
                    metadata,
                    transactionNow
            );
            session.persist(transaction);
            session.flush();
            persistEntry(session, transaction.getId(), balance, "TARGET", BigDecimal.ZERO, unchanged, unchanged);
            session.flush();
            return outcome(
                    EconomyResultStatus.SUCCESS,
                    transaction.getOperationId(),
                    unchanged,
                    null,
                    "",
                    snapshot(balance, settings),
                    null
            );
        }));
    }

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
        TransactionType type = frozen ? TransactionType.ACCOUNT_FROZEN : TransactionType.ACCOUNT_UNFROZEN;
        String requestFingerprint = accountSettingFingerprint(
                type, identity, currency, actorPlayerId, actorName, reason, metadata
        );
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            MutationOutcome replay = replay(session, source, idempotencyKey, requestFingerprint);
            if (replay != null) {
                return replay;
            }
            TransactionClock provisioningClock = new TransactionClock(session);
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, provisioningClock, true);
            EconomyPlayerSettingsEntity settings = ensureSettings(
                    session, balance.getId(), currency, provisioningClock, true
            );
            long transactionNow = databaseNow(session);
            BigDecimal unchanged = balance.getBalance();
            settings.setAccountStatus(frozen ? AccountStatus.FROZEN.name() : AccountStatus.ACTIVE.name());
            settings.setStatusActorPlayerId(actorPlayerId);
            settings.setStatusReason(trim(reason, 255));
            settings.setUpdatedAt(transactionNow);
            EconomyTransactionEntity transaction = transaction(
                    type,
                    currency,
                    source,
                    idempotencyKey,
                    requestFingerprint,
                    actorPlayerId,
                    actorName,
                    reason,
                    metadata,
                    transactionNow
            );
            session.persist(transaction);
            session.flush();
            persistEntry(session, transaction.getId(), balance, "TARGET", BigDecimal.ZERO, unchanged, unchanged);
            session.flush();
            return outcome(
                    EconomyResultStatus.SUCCESS,
                    transaction.getOperationId(),
                    unchanged,
                    null,
                    "",
                    snapshot(balance, settings),
                    null
            );
        }));
    }

    public HistoryPage history(
            Identity identity,
            EconomySettings.Currency currency,
            int page,
            int pageSize
    ) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity account = ensureAccount(
                    session, identity, currency, new TransactionClock(session), false
            );
            List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                            "from EconomyTransactionEntryEntity where accountId = :accountId order by transactionId desc",
                            EconomyTransactionEntryEntity.class
                    )
                    .setParameter("accountId", account.getId())
                    .setFirstResult((page - 1) * pageSize)
                    .setMaxResults(pageSize + 1)
                    .getResultList();
            boolean hasMore = entries.size() > pageSize;
            if (hasMore) {
                entries = new ArrayList<>(entries.subList(0, pageSize));
            }
            List<HistoryItem> result = new ArrayList<>();
            for (EconomyTransactionEntryEntity entry : entries) {
                EconomyTransactionEntity transaction = session.find(EconomyTransactionEntity.class, entry.getTransactionId());
                if (transaction != null) {
                    result.add(new HistoryItem(
                            transaction.getId(),
                            UUID.fromString(transaction.getOperationId()),
                            transaction.getTransactionType(),
                            entry.getDelta(),
                            entry.getBalanceAfter(),
                            transaction.getActorName(),
                            transaction.getReason(),
                            transaction.getCreatedAt()
                    ));
                }
            }
            return new HistoryPage(result, page, hasMore);
        }));
    }

    public List<TopEntry> top(EconomySettings.Currency currency, int offset, int limit) {
        return executeWithRetry(() -> orm.runInTransaction(session -> session.createSelectionQuery(
                        "from EconomyBalanceEntity where currencyId = :currency and scopeKey = :scope "
                                + "order by balance desc, playerId asc",
                        EconomyBalanceEntity.class
                )
                .setParameter("currency", currency.id())
                .setParameter("scope", currency.scope().key())
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(entity -> new TopEntry(
                        entity.getPlayerId(),
                        UUID.fromString(entity.getPlayerUuid()),
                        entity.getPlayerName(),
                        entity.getBalance()
                ))
                .toList()));
    }

    public VerificationReport verify() {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            long accounts = session.createSelectionQuery("select count(*) from EconomyBalanceEntity", Long.class)
                    .getSingleResult();
            long transactions = session.createSelectionQuery("select count(*) from EconomyTransactionEntity", Long.class)
                    .getSingleResult();
            long orphanSettings = session.createSelectionQuery(
                            "select count(*) from EconomyPlayerSettingsEntity s where not exists "
                                    + "(select 1 from EconomyBalanceEntity b where b.id = s.accountId)",
                            Long.class
                    )
                    .getSingleResult();
            long transactionsWithoutEntries = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntity t where not exists "
                                    + "(select 1 from EconomyTransactionEntryEntity e where e.transactionId = t.id)",
                            Long.class
                    )
                    .getSingleResult();
            long invalidEntries = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.balanceBefore + e.delta <> e.balanceAfter",
                            Long.class
                    )
                    .getSingleResult();
            long invalidTransactions = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntity t where "
                                    + "t.transactionType not in :knownTypes or "
                                    + "(t.transactionType = :transfer and ("
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id) <> 2 or "
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id and e.entryRole = :sender) <> 1 or "
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id and e.entryRole = :recipient) <> 1 or "
                                    + "(select count(distinct e.accountId) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id) <> 2 or "
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id and e.entryRole = :sender and e.delta < 0) <> 1 or "
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id and e.entryRole = :recipient and e.delta > 0) <> 1 or "
                                    + "(select sum(e.delta) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id) <> 0)) or "
                                    + "(t.transactionType <> :transfer and ("
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id) <> 1 or "
                                    + "(select count(*) from EconomyTransactionEntryEntity e "
                                    + "where e.transactionId = t.id and e.entryRole = :target) <> 1))",
                            Long.class
                    )
                    .setParameter("knownTypes", java.util.Arrays.stream(TransactionType.values())
                            .map(Enum::name)
                            .toList())
                    .setParameter("transfer", TransactionType.TRANSFER.name())
                    .setParameter("sender", "SENDER")
                    .setParameter("recipient", "RECIPIENT")
                    .setParameter("target", "TARGET")
                    .getSingleResult();
            long orphanEntries = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntryEntity e where not exists "
                                    + "(select 1 from EconomyTransactionEntity t where t.id = e.transactionId) "
                                    + "or not exists (select 1 from EconomyBalanceEntity b where b.id = e.accountId)",
                            Long.class
                    )
                    .getSingleResult();
            long identityMismatches = session.createSelectionQuery(
                            "select count(*) from EconomyBalanceEntity b where not exists "
                                    + "(select 1 from EconomyPlayerIdentityEntity i where i.playerId = b.playerId "
                                    + "and i.playerUuid = b.playerUuid)",
                            Long.class
                    )
                    .getSingleResult();
            long entryAccountMismatches = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntryEntity e, "
                                    + "EconomyTransactionEntity t, EconomyBalanceEntity b "
                                    + "where t.id = e.transactionId and b.id = e.accountId and ("
                                    + "e.playerId <> b.playerId or t.currencyId <> b.currencyId "
                                    + "or t.scopeKey <> b.scopeKey)",
                            Long.class
                    )
                    .getSingleResult();
            long accountsWithoutEntries = session.createSelectionQuery(
                            "select count(*) from EconomyBalanceEntity b where not exists "
                                    + "(select 1 from EconomyTransactionEntryEntity e where e.accountId = b.id)",
                            Long.class
                    )
                    .getSingleResult();
            long balanceJournalMismatches = session.createSelectionQuery(
                            "select count(*) from EconomyBalanceEntity b where exists ("
                                    + "select 1 from EconomyTransactionEntryEntity e where e.accountId = b.id "
                                    + "and e.transactionId = (select max(latest.transactionId) "
                                    + "from EconomyTransactionEntryEntity latest where latest.accountId = b.id) "
                                    + "and e.balanceAfter <> b.balance)",
                            Long.class
                    )
                    .getSingleResult();
            long journalContinuityErrors = session.createSelectionQuery(
                            "select count(*) from EconomyTransactionEntryEntity e where exists ("
                                    + "select 1 from EconomyTransactionEntryEntity previous "
                                    + "where previous.accountId = e.accountId and previous.transactionId = ("
                                    + "select max(candidate.transactionId) from EconomyTransactionEntryEntity candidate "
                                    + "where candidate.accountId = e.accountId "
                                    + "and candidate.transactionId < e.transactionId) "
                                    + "and previous.balanceAfter <> e.balanceBefore)",
                            Long.class
                    )
                    .getSingleResult();
            long invalidBalances = session.createSelectionQuery(
                            "select count(*) from EconomyBalanceEntity b where not exists ("
                                    + "select 1 from EconomyCurrencyDefinitionEntity d where "
                                    + "d.currencyId = b.currencyId and d.scopeKey = b.scopeKey) or exists ("
                                    + "select 1 from EconomyCurrencyDefinitionEntity d where "
                                    + "d.currencyId = b.currencyId and d.scopeKey = b.scopeKey and "
                                    + "(b.balance < d.minimumBalance or b.balance > d.maximumBalance))",
                            Long.class
                    )
                    .getSingleResult();
            return new VerificationReport(
                    accounts,
                    transactions,
                    invalidBalances,
                    invalidEntries,
                    invalidTransactions,
                    orphanSettings,
                    orphanEntries,
                    identityMismatches,
                    entryAccountMismatches,
                    accountsWithoutEntries,
                    transactionsWithoutEntries,
                    balanceJournalMismatches,
                    journalContinuityErrors
            );
        }));
    }

    private EconomyBalanceEntity ensureAccount(
            Session session,
            Identity identity,
            EconomySettings.Currency currency,
            TransactionClock clock,
            boolean lock
    ) {
        ensurePlayerIdentity(session, identity, clock, lock);
        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());
        EconomyBalanceEntity account = lock
                ? session.find(EconomyBalanceEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyBalanceEntity.class, id);
        if (account == null) {
            return createAccount(session, id, identity, currency, clock);
        }
        validateAccountIdentity(account, identity);
        if (lock) {
            String playerName = trim(identity.playerName(), 32);
            if (!Objects.equals(account.getPlayerName(), playerName)) {
                account.setPlayerName(playerName);
                account.setUpdatedAt(clock.now());
            }
        }
        return account;
    }

    private EconomyBalanceEntity createAccount(
            Session session,
            String id,
            Identity identity,
            EconomySettings.Currency currency,
            TransactionClock clock
    ) {
        long now = clock.now();
        EconomyBalanceEntity account = new EconomyBalanceEntity();
        account.setId(id);
        account.setPlayerId(identity.playerId());
        account.setPlayerUuid(identity.playerUuid().toString());
        account.setPlayerName(trim(identity.playerName(), 32));
        account.setCurrencyId(currency.id());
        account.setScopeKey(currency.scope().key());
        BigDecimal startingBalance = databaseAmount(currency.balances().starting());
        account.setBalance(startingBalance);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        session.persist(account);
        session.flush();
        persistAccountCreation(session, account, currency, startingBalance, now);
        return account;
    }

    private static void validateAccountIdentity(EconomyBalanceEntity account, Identity identity) {
        if (account.getPlayerId() != identity.playerId()
                || !Objects.equals(account.getPlayerUuid(), identity.playerUuid().toString())) {
            throw new IllegalStateException(
                    "Economy account identity mismatch for player ID " + identity.playerId()
            );
        }
    }


    private EconomyPlayerIdentityEntity ensurePlayerIdentity(
            Session session,
            Identity identity,
            TransactionClock clock,
            boolean lock
    ) {
        EconomyPlayerIdentityEntity canonical = lock
                ? session.find(EconomyPlayerIdentityEntity.class, identity.playerId(), LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyPlayerIdentityEntity.class, identity.playerId());
        String playerUuid = identity.playerUuid().toString();
        String playerName = trim(identity.playerName(), 32);
        if (canonical == null) {
            EconomyPlayerIdentityEntity uuidOwner = session.createSelectionQuery(
                            "from EconomyPlayerIdentityEntity where playerUuid = :uuid",
                            EconomyPlayerIdentityEntity.class
                    )
                    .setParameter("uuid", playerUuid)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (uuidOwner != null && uuidOwner.getPlayerId() != identity.playerId()) {
                throw new IllegalStateException(
                        "Economy UUID " + playerUuid + " is already owned by player ID " + uuidOwner.getPlayerId()
                );
            }
            canonical = new EconomyPlayerIdentityEntity();
            long now = clock.now();
            canonical.setPlayerId(identity.playerId());
            canonical.setPlayerUuid(playerUuid);
            canonical.setPlayerName(playerName);
            canonical.setCreatedAt(now);
            canonical.setUpdatedAt(now);
            session.persist(canonical);
            session.flush();
            return canonical;
        }
        if (!Objects.equals(canonical.getPlayerUuid(), playerUuid)) {
            throw new IllegalStateException(
                    "Economy player ID " + identity.playerId() + " is already owned by UUID "
                            + canonical.getPlayerUuid()
            );
        }
        if (lock && !Objects.equals(canonical.getPlayerName(), playerName)) {
            canonical.setPlayerName(playerName);
            canonical.setUpdatedAt(clock.now());
        }
        return canonical;
    }


    private static void persistAccountCreation(
            Session session,
            EconomyBalanceEntity account,
            EconomySettings.Currency currency,
            BigDecimal startingBalance,
            long now
    ) {
        String idempotencyKey = "account:" + account.getId();
        String requestFingerprint = fingerprint(
                "account-creation-v1",
                account.getId(),
                currency.id(),
                currency.scope().key(),
                startingBalance.toPlainString()
        );
        EconomyTransactionEntity transaction = transaction(
                TransactionType.ACCOUNT_CREATED,
                currency,
                "economy-account",
                idempotencyKey,
                requestFingerprint,
                null,
                "system",
                "Economy account created",
                Map.of("account_id", account.getId()),
                now
        );
        session.persist(transaction);
        session.flush();
        persistEntry(
                session,
                transaction.getId(),
                account,
                "TARGET",
                startingBalance,
                BigDecimal.ZERO,
                startingBalance
        );
        session.flush();
    }

    private EconomyPlayerSettingsEntity ensureSettings(
            Session session,
            String accountId,
            EconomySettings.Currency currency,
            TransactionClock clock,
            boolean lock
    ) {
        EconomyPlayerSettingsEntity settings = lock
                ? session.find(EconomyPlayerSettingsEntity.class, accountId, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyPlayerSettingsEntity.class, accountId);
        if (settings == null) {
            return createSettings(session, accountId, currency, clock);
        }
        return settings;
    }

    private EconomyPlayerSettingsEntity createSettings(
            Session session,
            String accountId,
            EconomySettings.Currency currency,
            TransactionClock clock
    ) {
        long now = clock.now();
        EconomyPlayerSettingsEntity settings = new EconomyPlayerSettingsEntity();
        settings.setAccountId(accountId);
        settings.setPaymentsEnabled(currency.payments().defaultEnabled());
        settings.setAccountStatus(AccountStatus.ACTIVE.name());
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        session.persist(settings);
        session.flush();
        return settings;
    }

    private void applyDailyLimits(
            Session session,
            EconomyBalanceEntity sender,
            EconomyBalanceEntity recipient,
            EconomySettings.Currency currency,
            BigDecimal amount,
            long now
    ) {
        BigDecimal sendLimit = currency.payments().dailySendLimit();
        BigDecimal receiveLimit = currency.payments().dailyReceiveLimit();
        if (sendLimit.signum() <= 0 && receiveLimit.signum() <= 0) {
            return;
        }
        String date = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString();
        EconomyDailyUsageEntity senderUsage;
        EconomyDailyUsageEntity recipientUsage;
        if (sender.getId().compareTo(recipient.getId()) < 0) {
            senderUsage = usage(session, sender, date, now);
            recipientUsage = usage(session, recipient, date, now);
        } else {
            recipientUsage = usage(session, recipient, date, now);
            senderUsage = usage(session, sender, date, now);
        }

        BigDecimal sent = senderUsage.getSentAmount().add(amount);
        if (sendLimit.signum() > 0 && sent.compareTo(sendLimit) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Daily send limit exceeded");
        }
        BigDecimal received = recipientUsage.getReceivedAmount().add(amount);
        if (receiveLimit.signum() > 0 && received.compareTo(receiveLimit) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Daily receive limit exceeded");
        }

        senderUsage.setSentAmount(databaseAmount(sent));
        senderUsage.setSentCount(Math.addExact(senderUsage.getSentCount(), 1));
        senderUsage.setUpdatedAt(now);
        recipientUsage.setReceivedAmount(databaseAmount(received));
        recipientUsage.setUpdatedAt(now);
    }

    private EconomyDailyUsageEntity usage(
            Session session,
            EconomyBalanceEntity account,
            String date,
            long now
    ) {
        String id = account.getId() + ":" + date;
        EconomyDailyUsageEntity usage = session.find(
                EconomyDailyUsageEntity.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (usage != null) {
            return usage;
        }
        usage = new EconomyDailyUsageEntity();
        usage.setId(id);
        usage.setAccountId(account.getId());
        usage.setUsageDate(date);
        usage.setSentAmount(databaseAmount(BigDecimal.ZERO));
        usage.setReceivedAmount(databaseAmount(BigDecimal.ZERO));
        usage.setSentCount(0);
        usage.setUpdatedAt(now);
        session.persist(usage);
        session.flush();
        return usage;
    }

    private MutationOutcome replay(
            Session session,
            String source,
            String idempotencyKey,
            String requestFingerprint
    ) {
        String normalizedSource = bounded(source, 64, "source", true);
        String normalizedKey = bounded(idempotencyKey, 160, "idempotencyKey", true);
        EconomyTransactionEntity transaction = session.createSelectionQuery(
                        "from EconomyTransactionEntity where source = :source and idempotencyKeyHash = :keyHash",
                        EconomyTransactionEntity.class
                )
                .setParameter("source", normalizedSource)
                .setParameter("keyHash", hash(normalizedKey))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (transaction == null) {
            return null;
        }
        if (!Objects.equals(transaction.getIdempotencyKey(), normalizedKey)
                || !Objects.equals(transaction.getRequestFingerprint(), requestFingerprint)) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different economy request"
            );
        }
        List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                        "from EconomyTransactionEntryEntity where transactionId = :transactionId order by id asc",
                        EconomyTransactionEntryEntity.class
                )
                .setParameter("transactionId", transaction.getId())
                .getResultList();
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    "Economy transaction " + transaction.getOperationId() + " has no journal entries"
            );
        }
        BigDecimal balance = null;
        BigDecimal counterpartBalance = null;
        Account account = null;
        Account counterpart = null;
        for (EconomyTransactionEntryEntity entry : entries) {
            EconomyBalanceEntity balanceEntity = session.find(EconomyBalanceEntity.class, entry.getAccountId());
            EconomyPlayerSettingsEntity settingsEntity = session.find(
                    EconomyPlayerSettingsEntity.class,
                    entry.getAccountId()
            );
            if (balanceEntity == null || settingsEntity == null) {
                throw new IllegalStateException(
                        "Economy transaction " + transaction.getOperationId() + " references a missing account"
                );
            }
            Account snapshot = snapshot(balanceEntity, settingsEntity);
            if ("RECIPIENT".equals(entry.getEntryRole())) {
                counterpartBalance = entry.getBalanceAfter();
                counterpart = snapshot;
            } else {
                balance = entry.getBalanceAfter();
                account = snapshot;
            }
        }
        return outcome(
                EconomyResultStatus.IDEMPOTENT_REPLAY,
                transaction.getOperationId(),
                balance,
                counterpartBalance,
                "",
                account,
                counterpart
        );
    }

    private static EconomyTransactionEntity transaction(
            TransactionType type,
            EconomySettings.Currency currency,
            String source,
            String idempotencyKey,
            String requestFingerprint,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            long now
    ) {
        EconomyTransactionEntity entity = new EconomyTransactionEntity();
        entity.setOperationId(UUID.randomUUID().toString());
        entity.setSource(bounded(source, 64, "source", true));
        String normalizedKey = bounded(idempotencyKey, 160, "idempotencyKey", true);
        entity.setIdempotencyKey(normalizedKey);
        entity.setIdempotencyKeyHash(hash(normalizedKey));
        entity.setRequestFingerprint(requestFingerprint);
        entity.setTransactionType(type.name());
        entity.setCurrencyId(currency.id());
        entity.setScopeKey(currency.scope().key());
        entity.setActorPlayerId(actorPlayerId);
        entity.setActorName(bounded(normalizedActor(actorName), 64, "actorName", true));
        entity.setReason(bounded(reason == null ? "" : reason, 255, "reason", false));
        String json = GSON.toJson(metadata == null ? Map.of() : metadata);
        if (json.length() > 4096) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.INVALID_AMOUNT,
                    "Economy metadata exceeds 4096 serialized characters"
            );
        }
        entity.setMetadataJson(json);
        entity.setCreatedAt(now);
        return entity;
    }

    private static void persistEntry(
            Session session,
            long transactionId,
            EconomyBalanceEntity account,
            String role,
            BigDecimal delta,
            BigDecimal before,
            BigDecimal after
    ) {
        EconomyTransactionEntryEntity entry = new EconomyTransactionEntryEntity();
        entry.setTransactionId(transactionId);
        entry.setAccountId(account.getId());
        entry.setPlayerId(account.getPlayerId());
        entry.setEntryRole(role);
        entry.setDelta(databaseAmount(delta));
        entry.setBalanceBefore(databaseAmount(before));
        entry.setBalanceAfter(databaseAmount(after));
        session.persist(entry);
    }

    private static Identity identity(EconomyBalanceEntity balance) {
        return new Identity(
                balance.getPlayerId(),
                UUID.fromString(balance.getPlayerUuid()),
                balance.getPlayerName()
        );
    }

    private static Account snapshot(EconomyBalanceEntity balance, EconomyPlayerSettingsEntity settings) {
        return new Account(
                balance.getId(),
                identity(balance),
                balance.getCurrencyId(),
                balance.getScopeKey(),
                balance.getBalance(),
                balance.getVersion(),
                settings.getVersion(),
                settings.isPaymentsEnabled(),
                AccountStatus.valueOf(settings.getAccountStatus())
        );
    }

    private static void requireActive(EconomyPlayerSettingsEntity settings, boolean bypassFreeze) {
        if (!bypassFreeze && AccountStatus.FROZEN.name().equals(settings.getAccountStatus())) {
            throw new EconomyRejectedException(EconomyResultStatus.ACCOUNT_FROZEN, "Account is frozen");
        }
    }

    private static void enforcePaymentCooldown(
            EconomyPlayerSettingsEntity senderSettings,
            EconomySettings.Currency currency,
            long now
    ) {
        long cooldownMillis = currency.payments().cooldown().toMillis();
        if (cooldownMillis <= 0L) {
            return;
        }
        Long previous = senderSettings.getLastPaymentAt();
        if (previous != null) {
            long elapsed = Math.max(0L, now - previous);
            if (elapsed < cooldownMillis) {
                long remaining = cooldownMillis - elapsed;
                throw new EconomyRejectedException(
                        EconomyResultStatus.LIMIT_EXCEEDED,
                        "Payment cooldown active for " + remaining + " ms"
                );
            }
        }
        senderSettings.setLastPaymentAt(now);
        senderSettings.setUpdatedAt(now);
    }

    static String mutationFingerprint(
            TransactionType operationType,
            TransactionType journalType,
            Identity identity,
            EconomySettings.Currency currency,
            BigDecimal amount,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            boolean bypassFreeze
    ) {
        return fingerprint(
                "mutation-v2",
                operationType.name(),
                journalType.name(),
                accountId(identity.playerId(), currency.id(), currency.scope().key()),
                identity.playerUuid().toString(),
                currency.id(),
                currency.scope().key(),
                amount.toPlainString(),
                actorPlayerId == null ? "" : actorPlayerId.toString(),
                normalizedActor(actorName),
                reason == null ? "" : reason.trim(),
                canonicalMetadata(metadata),
                Boolean.toString(bypassFreeze)
        );
    }

    static String transferFingerprint(
            Identity sender,
            Identity recipient,
            EconomySettings.Currency currency,
            BigDecimal amount,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            boolean bypassPaymentsToggle,
            boolean bypassFreeze
    ) {
        return fingerprint(
                "transfer-v2",
                accountId(sender.playerId(), currency.id(), currency.scope().key()),
                sender.playerUuid().toString(),
                accountId(recipient.playerId(), currency.id(), currency.scope().key()),
                recipient.playerUuid().toString(),
                currency.id(),
                currency.scope().key(),
                amount.toPlainString(),
                actorPlayerId == null ? "" : actorPlayerId.toString(),
                normalizedActor(actorName),
                reason == null ? "" : reason.trim(),
                canonicalMetadata(metadata),
                Boolean.toString(bypassPaymentsToggle),
                Boolean.toString(bypassFreeze)
        );
    }

    static String accountSettingFingerprint(
            TransactionType type,
            Identity identity,
            EconomySettings.Currency currency,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata
    ) {
        return fingerprint(
                "account-setting-v1",
                type.name(),
                accountId(identity.playerId(), currency.id(), currency.scope().key()),
                identity.playerUuid().toString(),
                currency.id(),
                currency.scope().key(),
                actorPlayerId == null ? "" : actorPlayerId.toString(),
                normalizedActor(actorName),
                reason == null ? "" : reason.trim(),
                canonicalMetadata(metadata)
        );
    }

    private static String normalizedActor(String actorName) {
        return actorName == null || actorName.isBlank() ? "system" : actorName.trim();
    }

    private static String canonicalMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(metadata).forEach((key, value) -> {
            appendFingerprintPart(builder, key == null ? "" : key);
            appendFingerprintPart(builder, value == null ? "" : value);
        });
        return builder.toString();
    }

    private static String fingerprint(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            appendFingerprintPart(builder, part == null ? "" : part);
        }
        return hash(builder.toString());
    }

    private static void appendFingerprintPart(StringBuilder builder, String part) {
        builder.append(part.length()).append(':').append(part).append(';');
    }

    private static BigDecimal normalizeMutationAmount(
            TransactionType type,
            BigDecimal amount,
            EconomySettings.Currency currency
    ) {
        if (type == TransactionType.SET) {
            BigDecimal normalized = normalize(amount, currency);
            validateBalance(normalized, currency);
            return normalized;
        }
        if (type == TransactionType.ACCOUNT_CREATED
                || type == TransactionType.PAYMENTS_ENABLED
                || type == TransactionType.PAYMENTS_DISABLED
                || type == TransactionType.ACCOUNT_FROZEN
                || type == TransactionType.ACCOUNT_UNFROZEN) {
            throw new IllegalArgumentException("Account-setting operations do not accept an amount");
        }
        return normalizePositive(amount, currency);
    }

    private static BigDecimal normalizePositive(BigDecimal amount, EconomySettings.Currency currency) {
        BigDecimal normalized = normalize(amount, currency);
        if (normalized.signum() <= 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount must be positive");
        }
        return normalized;
    }

    private static BigDecimal normalize(BigDecimal amount, EconomySettings.Currency currency) {
        if (amount == null) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount is required");
        }
        validateAmountShape(amount);
        try {
            return amount.setScale(currency.display().fractionalDigits(), currency.balances().rounding());
        } catch (ArithmeticException exception) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount has invalid precision");
        }
    }

    private static void validateAmountShape(BigDecimal amount) {
        long integerDigits = (long) amount.precision() - amount.scale();
        if (amount.scale() > DATABASE_SCALE || amount.signum() != 0 && integerDigits > 30L) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.INVALID_AMOUNT,
                    "Amount exceeds DECIMAL(38,8) storage precision"
            );
        }
    }

    private static void validateBalance(BigDecimal balance, EconomySettings.Currency currency) {
        if (!currency.balances().allowNegative() && balance.signum() < 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INSUFFICIENT_FUNDS, "Insufficient funds");
        }
        if (balance.compareTo(currency.balances().minimum()) < 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INSUFFICIENT_FUNDS, "Balance would fall below minimum");
        }
        if (balance.compareTo(currency.balances().maximum()) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Balance would exceed maximum");
        }
    }

    private <T> T executeWithRetry(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            int attemptNumber = attempt + 1;
            try {
                return work.get();
            } catch (EconomyRejectedException rejected) {
                throw rejected;
            } catch (RuntimeException failure) {
                last = failure;
                if (!isTransient(failure) || attemptNumber == MAX_RETRIES) {
                    throw failure;
                }
                try {
                    Thread.sleep(5L * attemptNumber);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
        throw last == null ? new IllegalStateException("Economy operation did not execute") : last;
    }

    private static long databaseNow(Session session) {
        return session.doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DATABASE_TIME_QUERY);
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("MySQL returned no authoritative Economy timestamp");
                }
                return result.getLong(1);
            }
        });
    }

    /** Lazily reads one authoritative timestamp for provisioning work in this transaction. */
    private static final class TransactionClock {
        private final Session session;
        private Long timestamp;

        private TransactionClock(Session session) {
            this.session = session;
        }

        private long now() {
            if (timestamp == null) {
                timestamp = databaseNow(session);
            }
            return timestamp;
        }
    }

    static boolean isTransient(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof LockTimeoutException
                    || current instanceof OptimisticLockException
                    || current instanceof PessimisticLockException) {
                return true;
            }
            if (current instanceof SQLException sqlException && isTransient(sqlException)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("deadlock")
                        || normalized.contains("lock wait timeout")
                        || normalized.contains("duplicate entry")
                        || normalized.contains("constraint") && normalized.contains("idempotency")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTransient(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            String sqlState = current.getSQLState();
            // MySQL: lock wait timeout, deadlock, and duplicate-key races during deterministic
            // account/idempotency creation. SQLSTATE 40001 also covers serialization failures.
            if (current.getErrorCode() == 1_205
                    || current.getErrorCode() == 1_213
                    || current.getErrorCode() == 1_062
                    || "40001".equals(sqlState)
                    || (sqlState != null && sqlState.startsWith("08"))) {
                return true;
            }
        }
        return false;
    }

    private static MutationOutcome outcome(
            EconomyResultStatus status,
            String operationId,
            BigDecimal balance,
            BigDecimal counterpartBalance,
            String message,
            Account account,
            Account counterpart
    ) {
        return new MutationOutcome(
                status,
                operationId == null ? null : UUID.fromString(operationId),
                balance,
                counterpartBalance,
                message == null ? "" : message,
                account,
                counterpart
        );
    }

    private static String accountId(long playerId, String currencyId, String scopeKey) {
        return playerId + ":" + currencyId + ":" + hash(scopeKey);
    }

    private static String definitionId(String currencyId, String scopeKey) {
        return currencyId + ":" + hash(scopeKey);
    }

    private static String definitionHash(EconomySettings.Currency currency) {
        return hash(String.join("|",
                currency.id(),
                currency.scope().type().name(),
                currency.scope().key(),
                Integer.toString(currency.display().fractionalDigits()),
                currency.balances().starting().toPlainString(),
                currency.balances().minimum().toPlainString(),
                currency.balances().maximum().toPlainString(),
                Boolean.toString(currency.balances().allowNegative()),
                currency.balances().rounding().name(),
                Boolean.toString(currency.payments().defaultEnabled()),
                currency.payments().minimum().toPlainString(),
                currency.payments().maximum().toPlainString(),
                currency.payments().confirmationThreshold().toPlainString(),
                currency.payments().dailySendLimit().toPlainString(),
                currency.payments().dailyReceiveLimit().toPlainString(),
                Long.toString(currency.payments().cooldown().toMillis())
        ));
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static BigDecimal databaseAmount(BigDecimal value) {
        BigDecimal normalized;
        try {
            normalized = value.setScale(DATABASE_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.INVALID_AMOUNT,
                    "Amount exceeds supported decimal precision"
            );
        }
        if (normalized.precision() > 38) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.INVALID_AMOUNT,
                    "Amount exceeds DECIMAL(38,8) storage precision"
            );
        }
        return normalized;
    }

    private static String bounded(String value, int maximum, String field, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, field + " must not be blank");
        }
        if (normalized.length() > maximum) {
            throw new EconomyRejectedException(
                    EconomyResultStatus.INVALID_AMOUNT,
                    field + " exceeds " + maximum + " characters"
            );
        }
        return normalized;
    }

    private static String trim(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
