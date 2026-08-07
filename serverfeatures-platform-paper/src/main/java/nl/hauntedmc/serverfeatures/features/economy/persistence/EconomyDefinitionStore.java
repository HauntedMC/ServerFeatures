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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Validates immutable cross-server currency definitions before Economy becomes available. */
final class EconomyDefinitionStore {
    void validate(Session session, EconomySettings settings) {
        long now = EconomyTransactionExecutor.databaseNow(session);
        for (EconomySettings.Currency currency : settings.currencies().values().stream()
                .sorted(Comparator.comparing(EconomySettings.Currency::id)).toList()) {
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
            } else {
                if (!hash.equals(entity.getDefinitionHash())) {
                    throw new IllegalStateException("Currency definition mismatch for " + currency.id()
                            + " in scope " + currency.scope().key());
                }
                if (entity.getDefinitionPayload() == null || entity.getDefinitionPayload().isBlank()) {
                    // Legacy rows contain a one-way hash only. A matching local configuration is
                    // the sole safe source from which to backfill the canonical import payload.
                    entity.setDefinitionPayload(EconomyDefinitionPayload.encode(canonicalDefinition));
                } else {
                    CurrencyDefinition stored = EconomyDefinitionPayload.decode(entity.getDefinitionPayload());
                    if (!hash.equals(EconomyPersistenceValues.definitionHash(stored))) {
                        throw new IllegalStateException("Currency definition payload mismatch for " + currency.id()
                                + " in scope " + currency.scope().key());
                    }
                }
                entity.setUpdatedAt(now);
            }
        }
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
            throw new IllegalStateException("Currency family mismatch for " + currency.id() + " in network "
                    + networkKey + ": scope type, precision, or global scope differs between servers");
        }
        family.setUpdatedAt(now);
    }
}
