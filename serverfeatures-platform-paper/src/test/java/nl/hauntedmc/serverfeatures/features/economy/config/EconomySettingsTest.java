package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomySettingsTest {

    @Test
    void acceptsServerGroupAndGlobalScopes() {
        EconomySettings.Currency server = currency("money", new EconomyScope(
                EconomyScopeType.SERVER, "hauntedmc/server/survival"
        ), "money");
        EconomySettings.Currency group = currency("shards", new EconomyScope(
                EconomyScopeType.GROUP, "hauntedmc/group/survival-network"
        ), "shards");
        EconomySettings.Currency global = currency("points", new EconomyScope(
                EconomyScopeType.GLOBAL, "hauntedmc/global"
        ), "points");

        EconomySettings settings = new EconomySettings(
                "hauntedmc",
                "survival",
                "system_data_rw",
                new EconomySettings.Vault(true, "money", EconomySettings.VaultConflictPolicy.FAIL),
                new EconomySettings.Messaging(true, "hauntedmc", "serverfeatures.economy.balance"),
                Map.of("money", server, "shards", group, "points", global)
        );

        assertEquals(EconomyScopeType.SERVER, settings.requireCurrency("money").scope().type());
        assertEquals(EconomyScopeType.GROUP, settings.requireCurrency("shards").scope().type());
        assertEquals(EconomyScopeType.GLOBAL, settings.requireCurrency("points").scope().type());
    }

    @Test
    void rejectsDuplicateCommandLabelsAcrossCurrencies() {
        EconomySettings.Currency money = currency("money", new EconomyScope(
                EconomyScopeType.SERVER, "hauntedmc/server/survival"
        ), "currency");
        EconomySettings.Currency points = currency("points", new EconomyScope(
                EconomyScopeType.GLOBAL, "hauntedmc/global"
        ), "currency");

        assertThrows(IllegalArgumentException.class, () -> new EconomySettings(
                "hauntedmc",
                "survival",
                "system_data_rw",
                new EconomySettings.Vault(false, "money", EconomySettings.VaultConflictPolicy.FAIL),
                new EconomySettings.Messaging(false, "hauntedmc", "serverfeatures.economy.balance"),
                Map.of("money", money, "points", points)
        ));
    }

    @Test
    void requiresEnabledVaultPrimaryCurrency() {
        EconomySettings.Currency points = currency("points", new EconomyScope(
                EconomyScopeType.GLOBAL, "hauntedmc/global"
        ), "points");

        assertThrows(IllegalArgumentException.class, () -> new EconomySettings(
                "hauntedmc",
                "survival",
                "system_data_rw",
                new EconomySettings.Vault(true, "money", EconomySettings.VaultConflictPolicy.FAIL),
                new EconomySettings.Messaging(false, "hauntedmc", "serverfeatures.economy.balance"),
                Map.of("points", points)
        ));
    }

    private static EconomySettings.Currency currency(String id, EconomyScope scope, String command) {
        return new EconomySettings.Currency(
                id,
                scope,
                new EconomySettings.Display(id, id, "", "{amount}", 2, true),
                new EconomySettings.Balances(
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("1000000.00"),
                        false,
                        RoundingMode.HALF_UP
                ),
                new EconomySettings.Commands(command, List.of(), true, true, true, true, true, false),
                new EconomySettings.Payments(
                        true,
                        true,
                        new BigDecimal("0.01"),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        Duration.ofSeconds(1)
                )
        );
    }
}
