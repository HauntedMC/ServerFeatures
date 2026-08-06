package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        EconomySettings settings = settings(
                "survival",
                Map.of("money", server, "shards", group, "points", global),
                true
        );

        assertEquals(EconomyScopeType.SERVER, settings.requireCurrency("money").scope().type());
        assertEquals(EconomyScopeType.GROUP, settings.requireCurrency("shards").scope().type());
        assertEquals(EconomyScopeType.GLOBAL, settings.requireCurrency("points").scope().type());
    }

    @Test
    void modelsHauntedNetworkGlobalAndGamemodeLocalCurrencies() {
        EconomySettings survival = networkSettings("survival");
        EconomySettings skyblock = networkSettings("skyblock");

        for (String global : List.of("crowns", "credits")) {
            assertEquals(
                    survival.requireCurrency(global).scope(),
                    skyblock.requireCurrency(global).scope(),
                    global + " must use one network-wide account"
            );
        }
        for (String local : List.of("essence", "relics", "soulstones", "money")) {
            assertEquals(EconomyScopeType.SERVER, survival.requireCurrency(local).scope().type());
            assertNotEquals(
                    survival.requireCurrency(local).scope().key(),
                    skyblock.requireCurrency(local).scope().key(),
                    local + " must have a separate balance per gamemode"
            );
        }
        assertEquals("money", survival.vault().primaryCurrency());
        assertEquals("money", skyblock.vault().primaryCurrency());
    }

    @Test
    void parsesHauntedNetworkTopologyFromConfiguration() {
        EconomySettings survival = load("survival", false);
        EconomySettings skyblock = load("skyblock", false);

        for (String global : List.of("crowns", "credits")) {
            assertEquals("hauntedmc/global", survival.requireCurrency(global).scope().key());
            assertEquals(
                    survival.requireCurrency(global).scope(),
                    skyblock.requireCurrency(global).scope()
            );
        }
        for (String local : List.of("essence", "relics", "soulstones", "money")) {
            assertEquals(
                    "hauntedmc/server/survival",
                    survival.requireCurrency(local).scope().key()
            );
            assertEquals(
                    "hauntedmc/server/skyblock",
                    skyblock.requireCurrency(local).scope().key()
            );
        }
        assertEquals("money", survival.vault().primaryCurrency());
    }

    @Test
    void supportsPerCurrencyLogicalLocalScopeOverridesForReplicas() {
        EconomySettings replicaOne = load("survival-1", true);
        EconomySettings replicaTwo = load("survival-2", true);

        assertEquals(
                replicaOne.requireCurrency("money").scope(),
                replicaTwo.requireCurrency("money").scope()
        );
        assertEquals("hauntedmc/server/survival", replicaOne.requireCurrency("money").scope().key());
    }

    @Test
    void rejectsDisablingPaymentsToKnownOfflineNetworkPlayers() {
        Map<String, Object> root = configuration("survival", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> currencies = (Map<String, Object>) root.get("currencies");
        @SuppressWarnings("unchecked")
        Map<String, Object> crowns = (Map<String, Object>) currencies.get("crowns");
        crowns.put("payments", Map.of("allow_offline_recipient", false));

        assertThrows(IllegalArgumentException.class, () -> load(root));
    }



    @Test
    void rejectsDuplicateNormalizedCurrencyIds() {
        Map<String, Object> root = configuration("survival", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> currencies = (Map<String, Object>) root.get("currencies");
        currencies.put("CROWNS", new LinkedHashMap<>(Map.of(
                "scope", Map.of("type", "GLOBAL")
        )));

        assertThrows(IllegalArgumentException.class, () -> load(root));
    }

    @Test
    void rejectsAmountsOutsideDecimalStorageShape() {
        Map<String, Object> root = configuration("survival", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> currencies = (Map<String, Object>) root.get("currencies");
        @SuppressWarnings("unchecked")
        Map<String, Object> crowns = (Map<String, Object>) currencies.get("crowns");
        crowns.put("balances", Map.of("maximum", "1000000000000000000000000000000"));

        assertThrows(IllegalArgumentException.class, () -> load(root));
    }

    @Test
    void rejectsDuplicateCommandLabelsAcrossCurrencies() {
        EconomySettings.Currency money = currency("money", new EconomyScope(
                EconomyScopeType.SERVER, "hauntedmc/server/survival"
        ), "currency");
        EconomySettings.Currency points = currency("points", new EconomyScope(
                EconomyScopeType.GLOBAL, "hauntedmc/global"
        ), "currency");

        assertThrows(IllegalArgumentException.class, () -> settings(
                "survival",
                Map.of("money", money, "points", points),
                false
        ));
    }

    @Test
    void requiresEnabledVaultPrimaryCurrency() {
        EconomySettings.Currency points = currency("points", new EconomyScope(
                EconomyScopeType.GLOBAL, "hauntedmc/global"
        ), "points");

        assertThrows(IllegalArgumentException.class, () -> settings(
                "survival",
                Map.of("points", points),
                true
        ));
    }

    private static EconomySettings load(String gamemode, boolean sharedReplicaScope) {
        return load(configuration(gamemode, sharedReplicaScope));
    }

    private static EconomySettings load(Map<String, Object> values) {
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(config.node()).thenReturn(ConfigNode.ofRaw(values, "economy"));
        return EconomySettings.load(config, "physical-server");
    }

    private static Map<String, Object> configuration(String gamemode, boolean sharedReplicaScope) {
        Map<String, Object> currencies = new LinkedHashMap<>();
        for (String global : List.of("crowns", "credits")) {
            currencies.put(global, new LinkedHashMap<>(Map.of(
                    "scope", Map.of("type", "GLOBAL")
            )));
        }
        for (String local : List.of("essence", "relics", "soulstones", "money")) {
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("type", "SERVER");
            if (sharedReplicaScope) {
                scope.put("local_key", "survival");
            }
            currencies.put(local, new LinkedHashMap<>(Map.of("scope", scope)));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("network_key", "hauntedmc");
        root.put("server_key", gamemode);
        root.put("vault", Map.of(
                "enabled", true,
                "primary_currency", "money",
                "conflict_policy", "FAIL"
        ));
        root.put("currencies", currencies);
        return root;
    }

    private static EconomySettings networkSettings(String gamemode) {
        Map<String, EconomySettings.Currency> currencies = new LinkedHashMap<>();
        currencies.put("crowns", currency(
                "crowns", new EconomyScope(EconomyScopeType.GLOBAL, "hauntedmc/global"), "crowns"
        ));
        currencies.put("credits", currency(
                "credits", new EconomyScope(EconomyScopeType.GLOBAL, "hauntedmc/global"), "credits"
        ));
        for (String local : List.of("essence", "relics", "soulstones", "money")) {
            currencies.put(local, currency(
                    local,
                    new EconomyScope(EconomyScopeType.SERVER, "hauntedmc/server/" + gamemode),
                    local
            ));
        }
        return settings(gamemode, currencies, true);
    }

    private static EconomySettings settings(
            String gamemode,
            Map<String, EconomySettings.Currency> currencies,
            boolean vaultEnabled
    ) {
        return new EconomySettings(
                "hauntedmc",
                gamemode,
                "system_data_rw",
                new EconomySettings.Vault(
                        vaultEnabled,
                        "money",
                        EconomySettings.VaultConflictPolicy.FAIL
                ),
                new EconomySettings.Messaging(true, "hauntedmc", "serverfeatures.economy.balance"),
                new EconomySettings.Cache(Duration.ofSeconds(10)),
                currencies
        );
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
                        new BigDecimal("0.01"),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        Duration.ofSeconds(1)
                )
        );
    }
}
