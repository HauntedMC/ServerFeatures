package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import org.hibernate.Session;

import java.util.Comparator;

/** Validates immutable cross-server currency definitions before Economy becomes available. */
final class EconomyDefinitionStore {
    void validate(Session session, EconomySettings settings) {
        long now = EconomyTransactionExecutor.databaseNow(session);
        for (EconomySettings.Currency currency : settings.currencies().values().stream()
                .sorted(Comparator.comparing(EconomySettings.Currency::id)).toList()) {
            validateFamily(session, settings.networkKey(), currency, now);
            String id = EconomyPersistenceValues.definitionId(currency.id(), currency.scope().key());
            String hash = EconomyPersistenceValues.definitionHash(currency);
            EconomyCurrencyDefinitionEntity entity = session.find(
                    EconomyCurrencyDefinitionEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (entity == null) {
                entity = new EconomyCurrencyDefinitionEntity();
                entity.setId(id);
                entity.setCurrencyId(currency.id());
                entity.setScopeKey(currency.scope().key());
                entity.setScopeType(currency.scope().type().name());
                entity.setFractionalDigits(currency.display().fractionalDigits());
                entity.setStartingBalance(EconomyPersistenceValues.databaseAmount(currency.balances().starting()));
                entity.setMinimumBalance(EconomyPersistenceValues.databaseAmount(currency.balances().minimum()));
                entity.setMaximumBalance(EconomyPersistenceValues.databaseAmount(currency.balances().maximum()));
                entity.setAllowNegative(currency.balances().allowNegative());
                entity.setDefinitionHash(hash);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                session.persist(entity);
            } else {
                if (!hash.equals(entity.getDefinitionHash())) {
                    throw new IllegalStateException("Currency definition mismatch for " + currency.id()
                            + " in scope " + currency.scope().key());
                }
                entity.setUpdatedAt(now);
            }
        }
    }

    private static void validateFamily(Session session, String networkKey,
                                       EconomySettings.Currency currency, long now) {
        String id = networkKey + ":" + currency.id();
        String globalScope = currency.scope().type() == EconomyScopeType.GLOBAL ? currency.scope().key() : null;
        String familyHash = EconomyPersistenceValues.hash(String.join("|", networkKey, currency.id(),
                currency.scope().type().name(), Integer.toString(currency.display().fractionalDigits()),
                globalScope == null ? "" : globalScope));
        EconomyCurrencyFamilyEntity family = session.find(
                EconomyCurrencyFamilyEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
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
            throw new IllegalStateException("Currency family mismatch for " + currency.id() + " in network "
                    + networkKey + ": scope type, precision, or global scope differs between servers");
        }
        family.setUpdatedAt(now);
    }
}
