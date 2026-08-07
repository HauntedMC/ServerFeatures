package nl.hauntedmc.serverfeatures.features.economy.placeholder;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EconomyPlaceholderTest {
    private static final UUID PLAYER_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private Economy feature;
    private EconomyService service;
    private OfflinePlayer player;
    private EconomyPlaceholder placeholder;

    @BeforeEach
    void setUp() {
        feature = mock(Economy.class);
        service = mock(EconomyService.class);
        player = mock(OfflinePlayer.class);
        placeholder = new EconomyPlaceholder(feature);
        when(feature.settings()).thenReturn(settings());
        when(feature.service()).thenReturn(service);
        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
    }

    @Test
    void exposesCachedAccountBalanceStateAndPreference() {
        Account account = new Account("money-account", new Identity(1L, PLAYER_UUID, "Alice"), "money",
                "hauntedmc/server/survival", new BigDecimal("12.50"), 3L, 2L, false, AccountStatus.FROZEN);
        when(service.cachedAccount(PLAYER_UUID, "money")).thenReturn(Optional.of(account));
        when(service.format("money", account.balance())).thenReturn("$12.50");

        assertEquals("$12.50", placeholder.onRequest(player, "money_balance"));
        assertEquals("12.50", placeholder.onRequest(player, "money_raw"));
        assertEquals("true", placeholder.onRequest(player, "money_available"));
        assertEquals("false", placeholder.onRequest(player, "money_payments"));
        assertEquals("true", placeholder.onRequest(player, "money_frozen"));
        assertEquals("frozen", placeholder.onRequest(player, "money_status"));
    }

    @Test
    void unavailableAccountUsesConfiguredPaymentDefaultAndExplicitAvailability() {
        when(service.cachedAccount(PLAYER_UUID, "money")).thenReturn(Optional.empty());

        assertEquals("0", placeholder.onRequest(player, "money_balance"));
        assertEquals("false", placeholder.onRequest(player, "money_available"));
        assertEquals("false", placeholder.onRequest(player, "money_payments"));
        assertEquals("false", placeholder.onRequest(player, "money_frozen"));
        assertEquals("unavailable", placeholder.onRequest(player, "money_status"));
    }

    @Test
    void primaryAndCurrencyMetadataDoNotNeedAPlayerOrCacheAccess() {
        assertEquals("money", placeholder.onRequest(null, "primary_currency"));
        assertEquals("$", placeholder.onRequest(null, "primary_symbol"));
        assertEquals("server", placeholder.onRequest(null, "money_scope_type"));
        assertEquals("server", placeholder.onRequest(null, "event_tokens_scope_type"));
        assertEquals("2", placeholder.onRequest(null, "money_fractional_digits"));
        verifyNoInteractions(service);
    }

    @Test
    void unknownOrMalformedRequestsRemainUnresolved() {
        assertNull(placeholder.onRequest(player, "money"));
        assertNull(placeholder.onRequest(player, "unknown_balance"));
        assertNull(placeholder.onRequest(player, "money_unknown"));
        assertNull(placeholder.onRequest(player, "x".repeat(129)));
    }

    private EconomySettings settings() {
        EconomySettings.Currency money = new EconomySettings.Currency("money",
                new EconomyScope(EconomyScopeType.SERVER, "hauntedmc/server/survival"),
                new EconomySettings.Display("coin", "coins", "$", "{symbol}{amount}", 2, true),
                new EconomySettings.Balances(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999999.99"), false,
                        RoundingMode.HALF_UP),
                new EconomySettings.Commands("money", List.of("balance"), true, true, true, true, true, true),
                new EconomySettings.Payments(false, new BigDecimal("0.01"), new BigDecimal("1000.00"),
                        new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, Duration.ofSeconds(1)));
        EconomySettings.Currency eventTokens = new EconomySettings.Currency("event_tokens",
                new EconomyScope(EconomyScopeType.SERVER, "hauntedmc/server/survival"),
                new EconomySettings.Display("token", "tokens", "T", "{symbol}{amount}", 0, true),
                new EconomySettings.Balances(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999999"), false,
                        RoundingMode.HALF_UP),
                new EconomySettings.Commands("tokens", List.of("tokens"), true, true, true, true, true, true),
                new EconomySettings.Payments(true, BigDecimal.ONE, new BigDecimal("1000"), new BigDecimal("100"),
                        BigDecimal.ZERO, BigDecimal.ZERO, Duration.ofSeconds(1)));
        return new EconomySettings("hauntedmc", "survival", "system_data_rw",
                new EconomySettings.Vault(true, "money", EconomySettings.VaultConflictPolicy.FAIL),
                new EconomySettings.Messaging(false, "hauntedmc", "economy"),
                new EconomySettings.Cache(Duration.ofSeconds(10)), Map.of("money", money, "event_tokens", eventTokens));
    }
}
