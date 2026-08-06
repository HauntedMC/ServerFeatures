package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Reconstructs successful outcomes for matching idempotent requests and rejects key reuse. */
final class EconomyIdempotencyStore {
    MutationOutcome replay(Session session, String source, String idempotencyKey, String requestFingerprint) {
        String normalizedSource = EconomyPersistenceValues.bounded(source, 64, "source", true);
        String normalizedKey = EconomyPersistenceValues.bounded(idempotencyKey, 160, "idempotencyKey", true);
        EconomyTransactionEntity transaction = session.createSelectionQuery(
                        "from EconomyTransactionEntity where source = :source and idempotencyKeyHash = :keyHash",
                        EconomyTransactionEntity.class)
                .setParameter("source", normalizedSource)
                .setParameter("keyHash", EconomyPersistenceValues.hash(normalizedKey))
                .setMaxResults(1).getResultStream().findFirst().orElse(null);
        if (transaction == null) return null;
        if (!Objects.equals(transaction.getIdempotencyKey(), normalizedKey)
                || !Objects.equals(transaction.getRequestFingerprint(), requestFingerprint)) {
            throw new EconomyRejectedException(EconomyResultStatus.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different economy request");
        }
        List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                        "from EconomyTransactionEntryEntity where transactionId = :transactionId order by id asc",
                        EconomyTransactionEntryEntity.class)
                .setParameter("transactionId", transaction.getId()).getResultList();
        if (entries.isEmpty()) {
            throw new IllegalStateException("Economy transaction " + transaction.getOperationId() + " has no journal entries");
        }
        BigDecimal balance = null;
        BigDecimal counterpartBalance = null;
        Account account = null;
        Account counterpart = null;
        for (EconomyTransactionEntryEntity entry : entries) {
            EconomyBalanceEntity balanceEntity = session.find(EconomyBalanceEntity.class, entry.getAccountId());
            EconomyPlayerSettingsEntity settingsEntity = session.find(EconomyPlayerSettingsEntity.class, entry.getAccountId());
            if (balanceEntity == null || settingsEntity == null) {
                throw new IllegalStateException("Economy transaction " + transaction.getOperationId()
                        + " references a missing account");
            }
            Account snapshot = EconomyPersistenceValues.snapshot(balanceEntity, settingsEntity);
            if ("RECIPIENT".equals(entry.getEntryRole())) {
                counterpartBalance = entry.getBalanceAfter();
                counterpart = snapshot;
            } else {
                balance = entry.getBalanceAfter();
                account = snapshot;
            }
        }
        return EconomyPersistenceValues.outcome(EconomyResultStatus.IDEMPOTENT_REPLAY,
                transaction.getOperationId(), balance, counterpartBalance, "", account, counterpart);
    }
}
