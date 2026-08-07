package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/** Serializes the complete, immutable monetary policy that a definition hash cannot be reversed into. */
final class EconomyDefinitionPayload {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    private EconomyDefinitionPayload() {
    }

    static CurrencyDefinition fromCurrency(EconomySettings.Currency currency) {
        return new CurrencyDefinition(
                currency.id(), currency.scope(), currency.display().fractionalDigits(),
                currency.balances().starting(), currency.balances().minimum(), currency.balances().maximum(),
                currency.balances().allowNegative(), currency.balances().rounding(),
                currency.payments().defaultEnabled(), currency.payments().minimum(), currency.payments().maximum(),
                currency.payments().confirmationThreshold(), currency.payments().dailySendLimit(),
                currency.payments().dailyReceiveLimit(), currency.payments().cooldown()
        );
    }

    static String encode(CurrencyDefinition definition) {
        return GSON.toJson(new Payload(
                SCHEMA_VERSION, definition.currencyId(), definition.scope().type().name(), definition.scope().key(),
                definition.fractionalDigits(), definition.startingBalance().toPlainString(),
                definition.minimumBalance().toPlainString(), definition.maximumBalance().toPlainString(),
                definition.allowNegative(), definition.rounding().name(), definition.paymentsDefaultEnabled(),
                definition.paymentMinimum().toPlainString(), definition.paymentMaximum().toPlainString(),
                definition.confirmationThreshold().toPlainString(), definition.dailySendLimit().toPlainString(),
                definition.dailyReceiveLimit().toPlainString(), definition.paymentCooldown().toMillis()
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
                    new BigDecimal(value.maximumBalance()), value.allowNegative(), RoundingMode.valueOf(value.rounding()),
                    value.paymentsDefaultEnabled(), new BigDecimal(value.paymentMinimum()),
                    new BigDecimal(value.paymentMaximum()), new BigDecimal(value.confirmationThreshold()),
                    new BigDecimal(value.dailySendLimit()), new BigDecimal(value.dailyReceiveLimit()),
                    Duration.ofMillis(value.paymentCooldownMillis())
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid stored currency-definition payload", exception);
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
            String rounding,
            boolean paymentsDefaultEnabled,
            String paymentMinimum,
            String paymentMaximum,
            String confirmationThreshold,
            String dailySendLimit,
            String dailyReceiveLimit,
            long paymentCooldownMillis
    ) {
    }
}
