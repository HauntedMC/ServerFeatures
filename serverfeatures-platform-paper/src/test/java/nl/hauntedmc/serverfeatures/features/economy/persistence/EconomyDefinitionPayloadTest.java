package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyDefinitionPayloadTest {

    @Test
    void preservesEveryFingerprintFieldThroughRoundTrip() {
        EconomySettings.Currency currency = currency();

        var definition = EconomyDefinitionPayload.fromCurrency(currency);
        var restored = EconomyDefinitionPayload.decode(EconomyDefinitionPayload.encode(definition));

        assertEquals(definition, restored);
        assertEquals(EconomyPersistenceValues.definitionHash(currency), EconomyPersistenceValues.definitionHash(restored));
    }

    @Test
    void rejectsMalformedPayloads() {
        assertThrows(IllegalStateException.class, () -> EconomyDefinitionPayload.decode("{not-json}"));
    }

    @Test
    void rejectsPayloadsThatViolateTheMonetaryPolicyInvariants() {
        String invalidCooldown = """
                {"schemaVersion":1,"currencyId":"money","scopeType":"GLOBAL","scopeKey":"hauntedmc/global",
                "fractionalDigits":2,"startingBalance":"0.00","minimumBalance":"0.00","maximumBalance":"10.00",
                "allowNegative":false,"rounding":"HALF_EVEN","paymentsDefaultEnabled":true,"paymentMinimum":"0.01",
                "paymentMaximum":"0.00","confirmationThreshold":"0.00","dailySendLimit":"0.00",
                "dailyReceiveLimit":"0.00","paymentCooldownMillis":-1}
                """;

        assertThrows(IllegalStateException.class, () -> EconomyDefinitionPayload.decode(invalidCooldown));
    }

    @Test
    void rejectsPayloadsWhoseAmountsCannotBePersistedSafely() {
        String oversizedBalance = """
                {"schemaVersion":1,"currencyId":"money","scopeType":"GLOBAL","scopeKey":"hauntedmc/global",
                "fractionalDigits":2,"startingBalance":"1000000000000000000000000000000.00","minimumBalance":"0.00","maximumBalance":"1000000000000000000000000000000.00",
                "allowNegative":false,"rounding":"HALF_EVEN","paymentsDefaultEnabled":true,"paymentMinimum":"0.01",
                "paymentMaximum":"0.00","confirmationThreshold":"0.00","dailySendLimit":"0.00",
                "dailyReceiveLimit":"0.00","paymentCooldownMillis":0}
                """;

        assertThrows(IllegalStateException.class, () -> EconomyDefinitionPayload.decode(oversizedBalance));
    }

    private static EconomySettings.Currency currency() {
        return new EconomySettings.Currency("survival_tokens",
                new EconomyScope(EconomyScopeType.GROUP, "hauntedmc/group/survival"),
                new EconomySettings.Display("token", "tokens", "T", "{symbol}{amount}", 2, true),
                new EconomySettings.Balances(new BigDecimal("4.00"), new BigDecimal("-5.00"),
                        new BigDecimal("1000.00"), true, RoundingMode.HALF_EVEN),
                new EconomySettings.Commands("tokens", List.of(), true, false, false, false, true, false),
                new EconomySettings.Payments(false, new BigDecimal("0.25"), new BigDecimal("50.00"),
                        new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), Duration.ofSeconds(3)));
    }
}
