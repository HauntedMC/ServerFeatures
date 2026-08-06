package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes audited monetary and account-setting commands.
 *
 * <p>Every method opens exactly one retryable ORM transaction. Account provisioning, row locks,
 * policy checks, balance changes, transaction headers, and journal entries therefore commit or
 * roll back as a single unit.</p>
 */
final class EconomyMutationStore {
    private final ORMContext orm;
    private final EconomyTransactionExecutor executor;
    private final EconomyAccountStore accounts;
    private final EconomyPaymentPolicy payments;
    private final EconomyIdempotencyStore idempotency;

    EconomyMutationStore(ORMContext orm, EconomyTransactionExecutor executor, EconomyAccountStore accounts,
                         EconomyPaymentPolicy payments, EconomyIdempotencyStore idempotency) {
        this.orm = orm;
        this.executor = executor;
        this.accounts = accounts;
        this.payments = payments;
        this.idempotency = idempotency;
    }

    MutationOutcome mutate(TransactionType type, Identity identity, EconomySettings.Currency currency,
                           BigDecimal rawAmount, String source, String idempotencyKey, Long actorPlayerId,
                           String actorName, String reason, Map<String, String> metadata, boolean bypassFreeze) {
        BigDecimal amount = EconomyPersistenceValues.normalizeMutationAmount(type, rawAmount, currency);
        String fingerprint = EconomyRequestFingerprint.mutation(type, type, identity, currency, amount,
                actorPlayerId, actorName, reason, metadata, bypassFreeze);
        try {
            return executor.execute(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = idempotency.replay(session, source, idempotencyKey, fingerprint);
                if (replay != null) return replay;
                EconomyTransactionExecutor.Clock clock = new EconomyTransactionExecutor.Clock(session);
                EconomyBalanceEntity balance = accounts.ensureAccount(session, identity, currency, clock, true);
                EconomyPlayerSettingsEntity settings = accounts.ensureSettings(session, balance.getId(), currency, clock, true);
                long now = EconomyTransactionExecutor.databaseNow(session);
                payments.requireActive(settings, bypassFreeze);
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
                EconomyPersistenceValues.validateBalance(after, currency);
                balance.setBalance(EconomyPersistenceValues.databaseAmount(after));
                balance.setPlayerName(EconomyPersistenceValues.trim(identity.playerName(), 32));
                balance.setPlayerUuid(identity.playerUuid().toString());
                balance.setUpdatedAt(now);
                EconomyTransactionEntity transaction = EconomyLedgerWriter.transaction(type, currency, source,
                        idempotencyKey, fingerprint, actorPlayerId, actorName, reason, metadata, now);
                session.persist(transaction);
                session.flush();
                EconomyLedgerWriter.persistEntry(session, transaction.getId(), balance, "TARGET",
                        after.subtract(before), before, after);
                session.flush();
                return EconomyPersistenceValues.outcome(EconomyResultStatus.SUCCESS, transaction.getOperationId(),
                        after, null, "", EconomyPersistenceValues.snapshot(balance, settings), null);
            }));
        } catch (EconomyRejectedException rejected) {
            return rejected(rejected);
        }
    }

    MutationOutcome transfer(Identity senderIdentity, Identity recipientIdentity,
                             EconomySettings.Currency currency, BigDecimal rawAmount, String source,
                             String idempotencyKey, Long actorPlayerId, String actorName, String reason,
                             Map<String, String> metadata, boolean bypassPaymentsToggle, boolean bypassFreeze) {
        BigDecimal amount = EconomyPersistenceValues.normalizePositive(rawAmount, currency);
        if (senderIdentity.playerId() == recipientIdentity.playerId()) {
            return failure(EconomyResultStatus.INVALID_AMOUNT, "Sender and recipient must differ");
        }
        if (amount.compareTo(currency.payments().minimum()) < 0) {
            return failure(EconomyResultStatus.LIMIT_EXCEEDED, "Amount is below the minimum payment");
        }
        if (currency.payments().maximum().signum() > 0
                && amount.compareTo(currency.payments().maximum()) > 0) {
            return failure(EconomyResultStatus.LIMIT_EXCEEDED, "Amount exceeds the maximum payment");
        }
        String fingerprint = EconomyRequestFingerprint.transfer(senderIdentity, recipientIdentity, currency, amount,
                actorPlayerId, actorName, reason, metadata, bypassPaymentsToggle, bypassFreeze);
        try {
            return executor.execute(() -> orm.runInTransaction(session -> {
                MutationOutcome replay = idempotency.replay(session, source, idempotencyKey, fingerprint);
                if (replay != null) return replay;
                List<Identity> lockOrder = new ArrayList<>(List.of(senderIdentity, recipientIdentity));
                lockOrder.sort(Comparator.comparingLong(Identity::playerId));
                EconomyTransactionExecutor.Clock clock = new EconomyTransactionExecutor.Clock(session);
                Map<Long, EconomyBalanceEntity> locked = new LinkedHashMap<>();
                Map<Long, EconomyPlayerSettingsEntity> settings = new LinkedHashMap<>();
                // Deterministic player-id locking prevents opposite-direction transfers from deadlocking.
                for (Identity identity : lockOrder) {
                    EconomyBalanceEntity account = accounts.ensureAccount(session, identity, currency, clock, true);
                    locked.put(identity.playerId(), account);
                    settings.put(identity.playerId(), accounts.ensureSettings(session, account.getId(), currency, clock, true));
                }
                EconomyBalanceEntity sender = locked.get(senderIdentity.playerId());
                EconomyBalanceEntity recipient = locked.get(recipientIdentity.playerId());
                EconomyPlayerSettingsEntity senderSettings = settings.get(senderIdentity.playerId());
                EconomyPlayerSettingsEntity recipientSettings = settings.get(recipientIdentity.playerId());
                long now = EconomyTransactionExecutor.databaseNow(session);
                payments.requireActive(senderSettings, bypassFreeze);
                payments.requireActive(recipientSettings, bypassFreeze);
                if (!bypassPaymentsToggle && !recipientSettings.isPaymentsEnabled()) {
                    throw new EconomyRejectedException(EconomyResultStatus.PAYMENTS_DISABLED,
                            "Recipient has disabled incoming payments");
                }
                BigDecimal senderBefore = sender.getBalance();
                BigDecimal recipientBefore = recipient.getBalance();
                BigDecimal senderAfter = senderBefore.subtract(amount);
                BigDecimal recipientAfter = recipientBefore.add(amount);
                EconomyPersistenceValues.validateBalance(senderAfter, currency);
                EconomyPersistenceValues.validateBalance(recipientAfter, currency);
                payments.enforceCooldown(senderSettings, currency, now);
                payments.applyDailyLimits(session, sender, recipient, currency, amount, now);
                updateBalance(sender, senderIdentity, senderAfter, now);
                updateBalance(recipient, recipientIdentity, recipientAfter, now);
                EconomyTransactionEntity transaction = EconomyLedgerWriter.transaction(TransactionType.TRANSFER,
                        currency, source, idempotencyKey, fingerprint, actorPlayerId, actorName, reason, metadata, now);
                session.persist(transaction);
                session.flush();
                EconomyLedgerWriter.persistEntry(session, transaction.getId(), sender, "SENDER",
                        amount.negate(), senderBefore, senderAfter);
                EconomyLedgerWriter.persistEntry(session, transaction.getId(), recipient, "RECIPIENT",
                        amount, recipientBefore, recipientAfter);
                session.flush();
                return EconomyPersistenceValues.outcome(EconomyResultStatus.SUCCESS, transaction.getOperationId(),
                        senderAfter, recipientAfter, "", EconomyPersistenceValues.snapshot(sender, senderSettings),
                        EconomyPersistenceValues.snapshot(recipient, recipientSettings));
            }));
        } catch (EconomyRejectedException rejected) {
            return rejected(rejected);
        }
    }

    MutationOutcome setPaymentsEnabled(Identity identity, EconomySettings.Currency currency, boolean enabled,
                                       String source, String idempotencyKey, Long actorPlayerId, String actorName,
                                       String reason, Map<String, String> metadata) {
        TransactionType type = enabled ? TransactionType.PAYMENTS_ENABLED : TransactionType.PAYMENTS_DISABLED;
        return setting(identity, currency, type, source, idempotencyKey, actorPlayerId, actorName, reason, metadata,
                settings -> settings.setPaymentsEnabled(enabled));
    }

    MutationOutcome setFrozen(Identity identity, EconomySettings.Currency currency, boolean frozen,
                              Long actorPlayerId, String actorName, String reason, String source,
                              String idempotencyKey, Map<String, String> metadata) {
        TransactionType type = frozen ? TransactionType.ACCOUNT_FROZEN : TransactionType.ACCOUNT_UNFROZEN;
        return setting(identity, currency, type, source, idempotencyKey, actorPlayerId, actorName, reason, metadata,
                settings -> {
                    settings.setAccountStatus(frozen ? AccountStatus.FROZEN.name() : AccountStatus.ACTIVE.name());
                    settings.setStatusActorPlayerId(actorPlayerId);
                    settings.setStatusReason(EconomyPersistenceValues.trim(reason, 255));
                });
    }

    private MutationOutcome setting(Identity identity, EconomySettings.Currency currency, TransactionType type,
                                    String source, String idempotencyKey, Long actorPlayerId, String actorName,
                                    String reason, Map<String, String> metadata,
                                    java.util.function.Consumer<EconomyPlayerSettingsEntity> change) {
        String fingerprint = EconomyRequestFingerprint.accountSetting(type, identity, currency, actorPlayerId,
                actorName, reason, metadata);
        return executor.execute(() -> orm.runInTransaction(session -> {
            MutationOutcome replay = idempotency.replay(session, source, idempotencyKey, fingerprint);
            if (replay != null) return replay;
            EconomyTransactionExecutor.Clock clock = new EconomyTransactionExecutor.Clock(session);
            EconomyBalanceEntity balance = accounts.ensureAccount(session, identity, currency, clock, true);
            EconomyPlayerSettingsEntity settings = accounts.ensureSettings(session, balance.getId(), currency, clock, true);
            long now = EconomyTransactionExecutor.databaseNow(session);
            change.accept(settings);
            settings.setUpdatedAt(now);
            EconomyTransactionEntity transaction = EconomyLedgerWriter.transaction(type, currency, source,
                    idempotencyKey, fingerprint, actorPlayerId, actorName, reason, metadata, now);
            session.persist(transaction);
            session.flush();
            EconomyLedgerWriter.persistEntry(session, transaction.getId(), balance, "TARGET", BigDecimal.ZERO,
                    balance.getBalance(), balance.getBalance());
            session.flush();
            return EconomyPersistenceValues.outcome(EconomyResultStatus.SUCCESS, transaction.getOperationId(),
                    balance.getBalance(), null, "", EconomyPersistenceValues.snapshot(balance, settings), null);
        }));
    }

    private static void updateBalance(EconomyBalanceEntity account, Identity identity, BigDecimal balance, long now) {
        account.setBalance(EconomyPersistenceValues.databaseAmount(balance));
        account.setPlayerName(EconomyPersistenceValues.trim(identity.playerName(), 32));
        account.setUpdatedAt(now);
    }

    private static MutationOutcome rejected(EconomyRejectedException rejected) {
        return failure(rejected.status(), rejected.getMessage());
    }

    private static MutationOutcome failure(EconomyResultStatus status, String message) {
        return EconomyPersistenceValues.outcome(status, null, null, null, message, null, null);
    }
}
