package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import jakarta.persistence.LockModeType;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
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
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Transactional MySQL repository. All balance mutations are committed atomically with their audit rows. */
public final class EconomyRepository {
    private static final int MAX_RETRIES = 3;
    private static final int DATABASE_SCALE = 8;
    private static final Gson GSON = new Gson();

    private final ORMContext orm;

    public EconomyRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public void validateDefinitions(EconomySettings settings, long now) {
        executeWithRetry(() -> orm.runInTransaction(session -> {
            for (EconomySettings.Currency currency : settings.currencies().values()) {
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

    public Account balance(Identity identity, EconomySettings.Currency currency, long now) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, now, false);
            EconomyPlayerSettingsEntity settings = ensureSettings(session, balance.getId(), currency, now, false);
            return snapshot(balance, settings);
        }));
    }

    public boolean accountExists(Identity identity, EconomySettings.Currency currency) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currency, "currency");
        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());
        return executeWithRetry(() -> orm.runInTransaction(session ->
                session.find(EconomyBalanceEntity.class, id) != null
        ));
    }

    public Optional<Identity> findIdentityByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> session.createSelectionQuery(
                        "from EconomyBalanceEntity where playerUuid = :uuid order by updatedAt desc",
                        EconomyBalanceEntity.class
                )
                .setParameter("uuid", uuid.toString())
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(entity -> new Identity(entity.getPlayerId(), uuid, entity.getPlayerName()))));
    }


    public Optional<Identity> findIdentityByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        return executeWithRetry(() -> orm.runInTransaction(session -> session.createSelectionQuery(
                        "from EconomyBalanceEntity where lower(playerName) = :name order by updatedAt desc",
                        EconomyBalanceEntity.class
                )
                .setParameter("name", playerName.trim().toLowerCase(java.util.Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(entity -> new Identity(
                        entity.getPlayerId(),
                        UUID.fromString(entity.getPlayerUuid()),
                        entity.getPlayerName()
                ))));
    }

    public MutationOutcome mutate(
            TransactionType operationType,
            TransactionType journalType,
            Identity identity,
            EconomySettings.Currency currency,
            BigDecimal rawAmount,
            String source,
            String idempotencyKey,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            boolean bypassFreeze,
            long now
    ) {
        requireCompatibleMutationTypes(operationType, journalType);
        BigDecimal amount = normalizeMutationAmount(operationType, rawAmount, currency);
        try {
            return executeWithRetry(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = replay(session, source, idempotencyKey);
                if (replay != null) {
                    return replay;
                }
                EconomyBalanceEntity balance = ensureAccount(session, identity, currency, now, true);
                EconomyPlayerSettingsEntity playerSettings = ensureSettings(
                        session,
                        balance.getId(),
                        currency,
                        now,
                        true
                );
                requireActive(playerSettings, bypassFreeze);

                BigDecimal before = balance.getBalance();
                BigDecimal after = switch (operationType) {
                    case DEPOSIT, ADMIN_ADD, LOTTERY_PAYOUT, LOTTERY_REFUND, VAULT_DEPOSIT -> before.add(amount);
                    case WITHDRAW, ADMIN_REMOVE, LOTTERY_PURCHASE, LOTTERY_DONATION, VAULT_WITHDRAW -> before.subtract(amount);
                    case SET, ADMIN_SET -> amount;
                    case TRANSFER -> throw new IllegalArgumentException("Use transfer() for transfers");
                };
                validateBalance(after, currency);
                balance.setBalance(databaseAmount(after));
                balance.setPlayerName(trim(identity.playerName(), 32));
                balance.setPlayerUuid(identity.playerUuid().toString());
                balance.setUpdatedAt(now);

                EconomyTransactionEntity transaction = transaction(
                        journalType, currency, source, idempotencyKey, actorPlayerId, actorName, reason, metadata, now
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
            boolean bypassFreeze,
            long now
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

        try {
            return executeWithRetry(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = replay(session, source, idempotencyKey);
                if (replay != null) {
                    return replay;
                }

                List<Identity> identities = new ArrayList<>(List.of(senderIdentity, recipientIdentity));
                identities.sort(Comparator.comparingLong(Identity::playerId));
                Map<Long, EconomyBalanceEntity> locked = new LinkedHashMap<>();
                Map<Long, EconomyPlayerSettingsEntity> settings = new LinkedHashMap<>();
                for (Identity identity : identities) {
                    EconomyBalanceEntity account = ensureAccount(session, identity, currency, now, true);
                    locked.put(identity.playerId(), account);
                    settings.put(identity.playerId(), ensureSettings(session, account.getId(), currency, now, true));
                }

                EconomyBalanceEntity sender = locked.get(senderIdentity.playerId());
                EconomyBalanceEntity recipient = locked.get(recipientIdentity.playerId());
                EconomyPlayerSettingsEntity senderSettings = settings.get(senderIdentity.playerId());
                EconomyPlayerSettingsEntity recipientSettings = settings.get(recipientIdentity.playerId());
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
                applyDailyLimit(session, sender, currency, amount, now);

                sender.setBalance(databaseAmount(senderAfter));
                sender.setPlayerName(trim(senderIdentity.playerName(), 32));
                sender.setUpdatedAt(now);
                recipient.setBalance(databaseAmount(recipientAfter));
                recipient.setPlayerName(trim(recipientIdentity.playerName(), 32));
                recipient.setUpdatedAt(now);

                EconomyTransactionEntity transaction = transaction(
                        TransactionType.TRANSFER,
                        currency,
                        source,
                        idempotencyKey,
                        actorPlayerId,
                        actorName,
                        reason,
                        metadata,
                        now
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

    public Account setPaymentsEnabled(
            Identity identity,
            EconomySettings.Currency currency,
            boolean enabled,
            long now
    ) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, now, true);
            EconomyPlayerSettingsEntity settings = ensureSettings(session, balance.getId(), currency, now, true);
            settings.setPaymentsEnabled(enabled);
            settings.setUpdatedAt(now);
            return snapshot(balance, settings);
        }));
    }

    public Account setFrozen(
            Identity identity,
            EconomySettings.Currency currency,
            boolean frozen,
            Long actorPlayerId,
            String reason,
            long now
    ) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity balance = ensureAccount(session, identity, currency, now, true);
            EconomyPlayerSettingsEntity settings = ensureSettings(session, balance.getId(), currency, now, true);
            settings.setAccountStatus(frozen ? AccountStatus.FROZEN.name() : AccountStatus.ACTIVE.name());
            settings.setStatusActorPlayerId(actorPlayerId);
            settings.setStatusReason(trim(reason, 255));
            settings.setUpdatedAt(now);
            return snapshot(balance, settings);
        }));
    }

    public HistoryPage history(
            Identity identity,
            EconomySettings.Currency currency,
            int page,
            int pageSize,
            long now
    ) {
        return executeWithRetry(() -> orm.runInTransaction(session -> {
            EconomyBalanceEntity account = ensureAccount(session, identity, currency, now, false);
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

    public VerificationReport verify(EconomySettings settings) {
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
            long invalidBalances = 0L;
            for (EconomySettings.Currency currency : settings.currencies().values()) {
                invalidBalances += session.createSelectionQuery(
                                "select count(*) from EconomyBalanceEntity where currencyId = :currency "
                                        + "and scopeKey = :scope and (balance < :minimum or balance > :maximum)",
                                Long.class
                        )
                        .setParameter("currency", currency.id())
                        .setParameter("scope", currency.scope().key())
                        .setParameter("minimum", databaseAmount(currency.balances().minimum()))
                        .setParameter("maximum", databaseAmount(currency.balances().maximum()))
                        .getSingleResult();
            }
            return new VerificationReport(
                    accounts,
                    transactions,
                    invalidBalances,
                    orphanSettings,
                    transactionsWithoutEntries
            );
        }));
    }

    private EconomyBalanceEntity ensureAccount(
            Session session,
            Identity identity,
            EconomySettings.Currency currency,
            long now,
            boolean lock
    ) {
        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());
        EconomyBalanceEntity account = lock
                ? session.find(EconomyBalanceEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyBalanceEntity.class, id);
        if (account == null) {
            account = new EconomyBalanceEntity();
            account.setId(id);
            account.setPlayerId(identity.playerId());
            account.setPlayerUuid(identity.playerUuid().toString());
            account.setPlayerName(trim(identity.playerName(), 32));
            account.setCurrencyId(currency.id());
            account.setScopeKey(currency.scope().key());
            account.setBalance(databaseAmount(currency.balances().starting()));
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            session.persist(account);
            session.flush();
        } else if (lock) {
            String playerUuid = identity.playerUuid().toString();
            String playerName = trim(identity.playerName(), 32);
            if (!Objects.equals(account.getPlayerUuid(), playerUuid)
                    || !Objects.equals(account.getPlayerName(), playerName)) {
                account.setPlayerUuid(playerUuid);
                account.setPlayerName(playerName);
                account.setUpdatedAt(now);
            }
        }
        return account;
    }

    private EconomyPlayerSettingsEntity ensureSettings(
            Session session,
            String accountId,
            EconomySettings.Currency currency,
            long now,
            boolean lock
    ) {
        EconomyPlayerSettingsEntity settings = lock
                ? session.find(EconomyPlayerSettingsEntity.class, accountId, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyPlayerSettingsEntity.class, accountId);
        if (settings == null) {
            settings = new EconomyPlayerSettingsEntity();
            settings.setAccountId(accountId);
            settings.setPaymentsEnabled(currency.payments().defaultEnabled());
            settings.setAccountStatus(AccountStatus.ACTIVE.name());
            settings.setCreatedAt(now);
            settings.setUpdatedAt(now);
            session.persist(settings);
            session.flush();
        }
        return settings;
    }

    private void applyDailyLimit(
            Session session,
            EconomyBalanceEntity sender,
            EconomySettings.Currency currency,
            BigDecimal amount,
            long now
    ) {
        BigDecimal limit = currency.payments().dailySendLimit();
        if (limit.signum() <= 0) {
            return;
        }
        String date = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString();
        String id = sender.getId() + ":" + date;
        EconomyDailyUsageEntity usage = session.find(EconomyDailyUsageEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (usage == null) {
            usage = new EconomyDailyUsageEntity();
            usage.setId(id);
            usage.setAccountId(sender.getId());
            usage.setUsageDate(date);
            usage.setSentAmount(databaseAmount(BigDecimal.ZERO));
            usage.setReceivedAmount(databaseAmount(BigDecimal.ZERO));
            usage.setSentCount(0);
            usage.setUpdatedAt(now);
            session.persist(usage);
            session.flush();
        }
        BigDecimal next = usage.getSentAmount().add(amount);
        if (next.compareTo(limit) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Daily send limit exceeded");
        }
        usage.setSentAmount(databaseAmount(next));
        usage.setSentCount(Math.addExact(usage.getSentCount(), 1));
        usage.setUpdatedAt(now);
    }

    private MutationOutcome replay(Session session, String source, String idempotencyKey) {
        EconomyTransactionEntity transaction = session.createSelectionQuery(
                        "from EconomyTransactionEntity where source = :source and idempotencyKey = :key",
                        EconomyTransactionEntity.class
                )
                .setParameter("source", trim(source, 64))
                .setParameter("key", trim(idempotencyKey, 160))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (transaction == null) {
            return null;
        }
        List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                        "from EconomyTransactionEntryEntity where transactionId = :transactionId order by id asc",
                        EconomyTransactionEntryEntity.class
                )
                .setParameter("transactionId", transaction.getId())
                .getResultList();
        BigDecimal balance = entries.isEmpty() ? null : entries.get(0).getBalanceAfter();
        BigDecimal counterpart = entries.size() < 2 ? null : entries.get(1).getBalanceAfter();
        return outcome(
                EconomyResultStatus.IDEMPOTENT_REPLAY,
                transaction.getOperationId(),
                balance,
                counterpart,
                "",
                null,
                null
        );
    }

    private static EconomyTransactionEntity transaction(
            TransactionType type,
            EconomySettings.Currency currency,
            String source,
            String idempotencyKey,
            Long actorPlayerId,
            String actorName,
            String reason,
            Map<String, String> metadata,
            long now
    ) {
        EconomyTransactionEntity entity = new EconomyTransactionEntity();
        entity.setOperationId(UUID.randomUUID().toString());
        entity.setSource(trim(source, 64));
        entity.setIdempotencyKey(trim(idempotencyKey, 160));
        entity.setTransactionType(type.name());
        entity.setCurrencyId(currency.id());
        entity.setScopeKey(currency.scope().key());
        entity.setActorPlayerId(actorPlayerId);
        entity.setActorName(trim(actorName == null ? "system" : actorName, 64));
        entity.setReason(trim(reason == null ? "" : reason, 255));
        String json = GSON.toJson(metadata == null ? Map.of() : metadata);
        entity.setMetadataJson(trim(json, 4096));
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

    private static Account snapshot(EconomyBalanceEntity balance, EconomyPlayerSettingsEntity settings) {
        return new Account(
                balance.getId(),
                new Identity(
                        balance.getPlayerId(),
                        UUID.fromString(balance.getPlayerUuid()),
                        balance.getPlayerName()
                ),
                balance.getCurrencyId(),
                balance.getScopeKey(),
                balance.getBalance(),
                balance.getVersion(),
                settings.isPaymentsEnabled(),
                AccountStatus.valueOf(settings.getAccountStatus())
        );
    }

    private static void requireActive(EconomyPlayerSettingsEntity settings, boolean bypassFreeze) {
        if (!bypassFreeze && AccountStatus.FROZEN.name().equals(settings.getAccountStatus())) {
            throw new EconomyRejectedException(EconomyResultStatus.ACCOUNT_FROZEN, "Account is frozen");
        }
    }

    private static void requireCompatibleMutationTypes(
            TransactionType operationType,
            TransactionType journalType
    ) {
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(journalType, "journalType");
        if (mutationDirection(operationType) != mutationDirection(journalType)) {
            throw new IllegalArgumentException(
                    "Journal transaction type " + journalType + " is incompatible with " + operationType
            );
        }
    }

    private static int mutationDirection(TransactionType type) {
        return switch (type) {
            case DEPOSIT, ADMIN_ADD, LOTTERY_PAYOUT, LOTTERY_REFUND, VAULT_DEPOSIT -> 1;
            case WITHDRAW, ADMIN_REMOVE, LOTTERY_PURCHASE, LOTTERY_DONATION, VAULT_WITHDRAW -> -1;
            case SET, ADMIN_SET -> 0;
            case TRANSFER -> 2;
        };
    }

    private static BigDecimal normalizeMutationAmount(
            TransactionType type,
            BigDecimal amount,
            EconomySettings.Currency currency
    ) {
        if (type == TransactionType.SET || type == TransactionType.ADMIN_SET) {
            BigDecimal normalized = normalize(amount, currency);
            validateBalance(normalized, currency);
            return normalized;
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
        try {
            return amount.setScale(currency.display().fractionalDigits(), currency.balances().rounding());
        } catch (ArithmeticException exception) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount has invalid precision");
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
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return work.get();
            } catch (EconomyRejectedException rejected) {
                throw rejected;
            } catch (RuntimeException failure) {
                last = failure;
                if (!isTransient(failure) || attempt == MAX_RETRIES) {
                    throw failure;
                }
                try {
                    Thread.sleep(5L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
        throw last == null ? new IllegalStateException("Economy operation did not execute") : last;
    }

    private static boolean isTransient(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
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
        return playerId + ":" + currencyId + ":" + hash(scopeKey).substring(0, 32);
    }

    private static String definitionId(String currencyId, String scopeKey) {
        return currencyId + ":" + hash(scopeKey).substring(0, 32);
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
                Boolean.toString(currency.balances().allowNegative())
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
        return value.setScale(DATABASE_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String trim(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
