package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Serializes the immutable currency identity that a definition hash cannot be reversed into. */
final class EconomyDefinitionPayload {
    private static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new Gson();

    private EconomyDefinitionPayload() {
    }

    static CurrencyDefinition fromCurrency(EconomySettings.Currency currency) {
        return new CurrencyDefinition(
                currency.id(), currency.scope(), currency.display().fractionalDigits(),
                currency.balances().starting(), currency.balances().minimum(), currency.balances().maximum(),
                currency.balances().allowNegative(), currency.balances().rounding()
        );
    }

    static String encode(CurrencyDefinition definition) {
        return GSON.toJson(new Payload(
                SCHEMA_VERSION, definition.currencyId(), definition.scope().type().name(), definition.scope().key(),
                definition.fractionalDigits(), definition.startingBalance().toPlainString(),
                definition.minimumBalance().toPlainString(), definition.maximumBalance().toPlainString(),
                definition.allowNegative(), definition.rounding().name()
        ));
    }

    static CurrencyDefinition decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Payload value = GSON.fromJson(payload, Payload.class);
            if (value == null || value.schemaVersion() != SCHEMA_VERSION) {
                throw new IllegalStateException("Unsupported currency-definition payload version");
            }
            return new CurrencyDefinition(
                    value.currencyId(), new EconomyScope(EconomyScopeType.valueOf(value.scopeType()), value.scopeKey()),
                    value.fractionalDigits(), new BigDecimal(value.startingBalance()), new BigDecimal(value.minimumBalance()),
                    new BigDecimal(value.maximumBalance()), value.allowNegative(), RoundingMode.valueOf(value.rounding())
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid stored currency-definition payload", exception);
        }
    }

    static boolean isCurrentSchema(String payload) {
        try {
            JsonObject object = GSON.fromJson(payload, JsonObject.class);
            return object != null && object.has("schemaVersion") && object.get("schemaVersion").getAsInt() == SCHEMA_VERSION;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record Payload(
            int schemaVersion,
            String currencyId,
            String scopeType,
            String scopeKey,
            int fractionalDigits,
            String startingBalance,
            String minimumBalance,
            String maximumBalance,
            boolean allowNegative,
            String rounding
    ) {
    }
}
