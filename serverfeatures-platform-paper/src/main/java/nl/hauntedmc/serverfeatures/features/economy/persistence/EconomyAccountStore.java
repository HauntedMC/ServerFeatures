package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerIdentityEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Provisions accounts, canonical player identities, and per-account settings inside a caller-owned transaction.
 *
 * <p>The caller chooses whether rows are locked. Mutation flows lock identity, balance, and settings rows in a
 * deterministic order; read flows can provision missing rows without imposing mutation locks.</p>
 */
final class EconomyAccountStore {
    EconomyBalanceEntity ensureAccount(Session session, Identity identity, EconomySettings.Currency currency,
                                       EconomyTransactionExecutor.Clock clock, boolean lock) {
        ensurePlayerIdentity(session, identity, clock, lock);
        String id = EconomyPersistenceValues.accountId(identity.playerId(), currency.id(), currency.scope().key());
        EconomyBalanceEntity account = lock
                ? session.find(EconomyBalanceEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyBalanceEntity.class, id);
        if (account == null) return createAccount(session, id, identity, currency, clock);
        validateIdentity(account, identity);
        if (lock) {
            String playerName = EconomyPersistenceValues.trim(identity.playerName(), 32);
            if (!Objects.equals(account.getPlayerName(), playerName)) {
                account.setPlayerName(playerName);
                account.setUpdatedAt(clock.now());
            }
        }
        return account;
    }

    EconomyPlayerSettingsEntity ensureSettings(Session session, String accountId, EconomySettings.Currency currency,
                                               EconomyTransactionExecutor.Clock clock, boolean lock) {
        EconomyPlayerSettingsEntity settings = lock
                ? session.find(EconomyPlayerSettingsEntity.class, accountId, LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyPlayerSettingsEntity.class, accountId);
        if (settings != null) return settings;
        long now = clock.now();
        settings = new EconomyPlayerSettingsEntity();
        settings.setAccountId(accountId);
        settings.setPaymentsEnabled(currency.payments().defaultEnabled());
        settings.setAccountStatus(AccountStatus.ACTIVE.name());
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        session.persist(settings);
        session.flush();
        return settings;
    }

    void validateIdentity(EconomyBalanceEntity account, Identity identity) {
        if (account.getPlayerId() != identity.playerId()
                || !Objects.equals(account.getPlayerUuid(), identity.playerUuid().toString())) {
            throw new IllegalStateException("Economy account identity mismatch for player ID " + identity.playerId());
        }
    }

    private EconomyBalanceEntity createAccount(Session session, String id, Identity identity,
                                                EconomySettings.Currency currency, EconomyTransactionExecutor.Clock clock) {
        long now = clock.now();
        EconomyBalanceEntity account = new EconomyBalanceEntity();
        account.setId(id);
        account.setPlayerId(identity.playerId());
        account.setPlayerUuid(identity.playerUuid().toString());
        account.setPlayerName(EconomyPersistenceValues.trim(identity.playerName(), 32));
        account.setCurrencyId(currency.id());
        account.setScopeKey(currency.scope().key());
        BigDecimal starting = EconomyPersistenceValues.databaseAmount(currency.balances().starting());
        account.setBalance(starting);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        session.persist(account);
        session.flush();
        persistCreation(session, account, currency, starting, now);
        return account;
    }

    void ensurePlayerIdentity(Session session, Identity identity, EconomyTransactionExecutor.Clock clock,
                              boolean lock) {
        EconomyPlayerIdentityEntity canonical = lock
                ? session.find(EconomyPlayerIdentityEntity.class, identity.playerId(), LockModeType.PESSIMISTIC_WRITE)
                : session.find(EconomyPlayerIdentityEntity.class, identity.playerId());
        String uuid = identity.playerUuid().toString();
        String name = EconomyPersistenceValues.trim(identity.playerName(), 32);
        if (canonical == null) {
            EconomyPlayerIdentityEntity uuidOwner = session.createSelectionQuery(
                            "from EconomyPlayerIdentityEntity where playerUuid = :uuid", EconomyPlayerIdentityEntity.class)
                    .setParameter("uuid", uuid).setMaxResults(1).getResultStream().findFirst().orElse(null);
            if (uuidOwner != null && uuidOwner.getPlayerId() != identity.playerId()) {
                throw new IllegalStateException("Economy UUID " + uuid + " is already owned by player ID " + uuidOwner.getPlayerId());
            }
            canonical = new EconomyPlayerIdentityEntity();
            long now = clock.now();
            canonical.setPlayerId(identity.playerId());
            canonical.setPlayerUuid(uuid);
            canonical.setPlayerName(name);
            canonical.setCreatedAt(now);
            canonical.setUpdatedAt(now);
            session.persist(canonical);
            session.flush();
            return;
        }
        if (!Objects.equals(canonical.getPlayerUuid(), uuid)) {
            throw new IllegalStateException("Economy player ID " + identity.playerId()
                    + " is already owned by UUID " + canonical.getPlayerUuid());
        }
        if (lock && !Objects.equals(canonical.getPlayerName(), name)) {
            canonical.setPlayerName(name);
            canonical.setUpdatedAt(clock.now());
        }
    }

    private static void persistCreation(Session session, EconomyBalanceEntity account,
                                        EconomySettings.Currency currency, BigDecimal starting, long now) {
        String fingerprint = EconomyRequestFingerprint.fingerprint("account-creation-v1", account.getId(),
                currency.id(), currency.scope().key(), starting.toPlainString());
        EconomyTransactionEntity transaction = EconomyLedgerWriter.transaction(TransactionType.ACCOUNT_CREATED,
                currency, "economy-account", "account:" + account.getId(), fingerprint, null, "system",
                "Economy account created", Map.of("account_id", account.getId()), now);
        session.persist(transaction);
        session.flush();
        EconomyLedgerWriter.persistEntry(session, transaction.getId(), account, "TARGET", starting,
                BigDecimal.ZERO, starting);
        EconomyLedgerWriter.persistSystemEntry(session, transaction.getId(), currency, starting.negate());
        session.flush();
    }
}
