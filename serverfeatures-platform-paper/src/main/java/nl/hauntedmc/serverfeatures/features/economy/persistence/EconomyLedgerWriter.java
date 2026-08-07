package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Creates immutable transaction headers and their double-entry journal rows. */
final class EconomyLedgerWriter {
    private static final Gson GSON = new Gson();

    private EconomyLedgerWriter() {
    }

    static EconomyTransactionEntity transaction(TransactionType type, EconomySettings.Currency currency,
            String source, String idempotencyKey, String fingerprint, Long actorPlayerId, String actorName,
            String reason, Map<String, String> metadata, long now) {
        EconomyTransactionEntity entity = new EconomyTransactionEntity();
        entity.setOperationId(UUID.randomUUID().toString());
        entity.setSource(EconomyPersistenceValues.bounded(source, 64, "source", true));
        String normalizedKey = EconomyPersistenceValues.bounded(idempotencyKey, 160, "idempotencyKey", true);
        entity.setIdempotencyKey(normalizedKey);
        entity.setIdempotencyKeyHash(EconomyPersistenceValues.hash(normalizedKey));
        entity.setRequestFingerprint(fingerprint);
        entity.setTransactionType(type.name());
        entity.setCurrencyId(currency.id());
        entity.setScopeKey(currency.scope().key());
        entity.setActorPlayerId(actorPlayerId);
        entity.setActorName(EconomyPersistenceValues.bounded(
                EconomyRequestFingerprint.normalizedActor(actorName), 64, "actorName", true));
        entity.setReason(EconomyPersistenceValues.bounded(reason == null ? "" : reason, 255, "reason", false));
        String json = GSON.toJson(metadata == null ? Map.of() : metadata);
        if (json.length() > 4096) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT,
                    "Economy metadata exceeds 4096 serialized characters");
        }
        entity.setMetadataJson(json);
        entity.setCreatedAt(now);
        return entity;
    }

    static void persistEntry(Session session, long transactionId, EconomyBalanceEntity account, String role,
                             BigDecimal delta, BigDecimal before, BigDecimal after) {
        persistEntry(session, transactionId, account.getId(), account.getPlayerId(), "PLAYER", role, delta, before, after);
    }

    /**
     * Records the counterpart of issuance, burn, and administrative adjustment operations.
     *
     * <p>System accounts are logical ledger accounts rather than a mutable aggregate balance row.
     * This deliberately avoids serializing every shop purchase through one global treasury lock;
     * their balance is reconciled from the append-only entries. Player entries retain before/after
     * snapshots because they back the authoritative current-balance projection.</p>
     */
    static void persistSystemEntry(Session session, long transactionId, EconomySettings.Currency currency,
                                   BigDecimal delta) {
        persistEntry(session, transactionId, systemIssuanceAccountId(currency), 0L, "SYSTEM", "SYSTEM",
                delta, null, null);
    }

    static String systemIssuanceAccountId(EconomySettings.Currency currency) {
        return "system:issuance:" + currency.id() + ":" + EconomyPersistenceValues.hash(currency.scope().key());
    }

    private static void persistEntry(Session session, long transactionId, String accountId, long playerId,
                                     String accountKind, String role, BigDecimal delta, BigDecimal before,
                                     BigDecimal after) {
        EconomyTransactionEntryEntity entry = new EconomyTransactionEntryEntity();
        entry.setTransactionId(transactionId);
        entry.setAccountId(accountId);
        entry.setPlayerId(playerId);
        entry.setAccountKind(accountKind);
        entry.setEntryRole(role);
        entry.setDelta(EconomyPersistenceValues.databaseAmount(delta));
        entry.setBalanceBefore(before == null ? null : EconomyPersistenceValues.databaseAmount(before));
        entry.setBalanceAfter(after == null ? null : EconomyPersistenceValues.databaseAmount(after));
        session.persist(entry);
    }
}
