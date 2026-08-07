package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.DiscoveredCurrencyDefinition;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Validates immutable cross-server currency definitions before Economy becomes available. */
final class EconomyDefinitionStore {
    void validate(Session session, EconomySettings settings, EconomySettings.Currency currency) {
        long now = EconomyTransactionExecutor.databaseNow(session);
        validateFamily(session, settings.networkKey(), currency, now);
        String id = EconomyPersistenceValues.definitionId(currency.id(), currency.scope().key());
        String hash = EconomyPersistenceValues.definitionHash(currency);
        CurrencyDefinition canonicalDefinition = EconomyDefinitionPayload.fromCurrency(currency);
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
            entity.setDefinitionPayload(EconomyDefinitionPayload.encode(canonicalDefinition));
            entity.setDefinitionHash(hash);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            session.persist(entity);
            return;
        }
        if (!matchesStoredIdentity(entity, currency)) {
            throw mismatch(currency, "scope, precision, starting balance or balance bounds differ");
        }
        if (EconomyDefinitionPayload.isCurrentSchema(entity.getDefinitionPayload())) {
            CurrencyDefinition stored;
            try {
                stored = EconomyDefinitionPayload.decode(entity.getDefinitionPayload());
            } catch (RuntimeException exception) {
                throw new EconomyDefinitionException("Invalid stored currency definition for " + currency.id(), exception);
            }
            if (!hash.equals(EconomyPersistenceValues.definitionHash(stored)) || !hash.equals(entity.getDefinitionHash())) {
                throw mismatch(currency, "the immutable definition differs");
            }
        }
        // Version 1 fingerprints included mutable payment policy. The durable columns above
        // establish compatibility, after which we atomically replace the old fingerprint with
        // the narrow v2 definition. This lets payment policy evolve without a data migration.
        entity.setDefinitionPayload(EconomyDefinitionPayload.encode(canonicalDefinition));
        entity.setDefinitionHash(hash);
        entity.setUpdatedAt(now);
    }

    /** Lists only global and group definitions belonging to the requested logical network. */
    List<DiscoveredCurrencyDefinition> discoverShared(Session session, String networkKey) {
        String globalScope = networkKey + "/global";
        String groupPrefix = networkKey + "/group/";
        return session.createSelectionQuery(
                        "from EconomyCurrencyDefinitionEntity where scopeType in :scopeTypes",
                        EconomyCurrencyDefinitionEntity.class
                )
                .setParameter("scopeTypes", List.of(EconomyScopeType.GLOBAL.name(), EconomyScopeType.GROUP.name()))
                .getResultList().stream()
                // Filter in Java rather than SQL LIKE: '_' is valid in network keys but is a LIKE wildcard.
                .filter(entity -> entity.getScopeKey().equals(globalScope) || entity.getScopeKey().startsWith(groupPrefix))
                .map(this::discovered)
                .sorted(Comparator.comparing(DiscoveredCurrencyDefinition::currencyId)
                        .thenComparing(value -> value.scope().key()))
                .toList();
    }

    Optional<DiscoveredCurrencyDefinition> sharedDefinition(
            Session session, String networkKey, String currencyId, String scopeKey
    ) {
        return discoverShared(session, networkKey).stream()
                .filter(definition -> definition.currencyId().equals(currencyId)
                        && definition.scope().key().equals(scopeKey))
                .findFirst();
    }

    private DiscoveredCurrencyDefinition discovered(EconomyCurrencyDefinitionEntity entity) {
        EconomyScopeType scopeType = EconomyScopeType.valueOf(entity.getScopeType());
        EconomyScope scope = new EconomyScope(scopeType, entity.getScopeKey());
        CurrencyDefinition definition = null;
        try {
            CurrencyDefinition candidate = EconomyDefinitionPayload.decode(entity.getDefinitionPayload());
            if (candidate != null
                    && candidate.currencyId().equals(entity.getCurrencyId())
                    && candidate.scope().equals(scope)
                    && entity.getDefinitionHash().equals(EconomyPersistenceValues.definitionHash(candidate))) {
                definition = candidate;
            }
        } catch (RuntimeException ignored) {
            // A corrupt or legacy payload is visible to administrators but cannot be imported.
        }
        return new DiscoveredCurrencyDefinition(entity.getCurrencyId(), scope, definition,
                entity.getCreatedAt(), entity.getUpdatedAt());
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
            throw new EconomyDefinitionException("Currency family mismatch for " + currency.id() + " in network "
                    + networkKey + ": scope type, precision, or global scope differs between servers");
        }
        family.setUpdatedAt(now);
    }

    private static boolean matchesStoredIdentity(EconomyCurrencyDefinitionEntity entity, EconomySettings.Currency currency) {
        return entity.getScopeType().equals(currency.scope().type().name())
                && entity.getFractionalDigits() == currency.display().fractionalDigits()
                && equalAmount(entity.getStartingBalance(), currency.balances().starting())
                && equalAmount(entity.getMinimumBalance(), currency.balances().minimum())
                && equalAmount(entity.getMaximumBalance(), currency.balances().maximum())
                && entity.isAllowNegative() == currency.balances().allowNegative();
    }

    private static boolean equalAmount(BigDecimal stored, BigDecimal configured) {
        return stored != null && configured != null && stored.compareTo(configured) == 0;
    }

    private static EconomyDefinitionException mismatch(EconomySettings.Currency currency, String detail) {
        return new EconomyDefinitionException("Currency definition mismatch for " + currency.id()
                + " in scope " + currency.scope().key() + ": " + detail);
    }
}
