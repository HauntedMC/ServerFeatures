package nl.hauntedmc.serverfeatures.api.hook;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderAPIHookTest {

    @BeforeEach
    void resetWarningLimiter() {
        PlaceholderAPIHook.clearWarningStateForTests();
    }

    @Test
    void returnsOriginalTextWhenPlayerIsNull() {
        AtomicBoolean resolverCalled = new AtomicBoolean(false);

        String output = PlaceholderAPIHook.applyPlaceholders(
                "hello",
                null,
                name -> true,
                (player, text) -> {
                    resolverCalled.set(true);
                    return "changed";
                }
        );

        assertEquals("hello", output);
        assertFalse(resolverCalled.get());
    }

    @Test
    void returnsOriginalTextWhenPluginIsDisabled() {
        Player player = player("Remy");
        AtomicBoolean resolverCalled = new AtomicBoolean(false);

        String output = PlaceholderAPIHook.applyPlaceholders(
                "hello",
                player,
                name -> false,
                (ignored, text) -> {
                    resolverCalled.set(true);
                    return "changed";
                }
        );

        assertEquals("hello", output);
        assertFalse(resolverCalled.get());
    }

    @Test
    void appliesResolverWhenPluginIsEnabledAndPlayerExists() {
        Player player = player("Remy");

        String output = PlaceholderAPIHook.applyPlaceholders(
                "hello %player%",
                player,
                name -> "PlaceholderAPI".equals(name),
                (resolvedPlayer, text) -> text.replace("%player%", resolvedPlayer.getName())
        );

        assertEquals("hello Remy", output);
        assertTrue(output.contains("Remy"));
    }

    @Test
    void preservesOriginalMessageAndRateLimitsExpansionFailures() {
        Player player = player("Remy");
        AtomicInteger warnings = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        String input = "Balance: %vault_eco_balance%";

        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(input, failingResolution(input, player, warnings, now));
        }
        assertEquals(1, warnings.get());

        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(input, failingResolution(input, player, warnings, now));
        assertEquals(2, warnings.get());
    }

    @Test
    void containsBinaryIncompatibilityErrorsFromExpansions() {
        Player player = player("Remy");
        AtomicInteger warnings = new AtomicInteger();
        String input = "Balance: %vault_eco_balance%";

        String output = PlaceholderAPIHook.applyPlaceholders(
                input,
                player,
                name -> true,
                (ignored, text) -> {
                    throw new NoSuchMethodError("incompatible expansion");
                },
                () -> true,
                (message, failure) -> warnings.incrementAndGet(),
                System::nanoTime
        );

        assertEquals(input, output);
        assertEquals(1, warnings.get());
    }

    @Test
    void leavesVaultEconomyPlaceholdersUnresolvedWhenProviderIsUnavailable() {
        Player player = player("Remy");
        AtomicBoolean resolverSawVaultPlaceholder = new AtomicBoolean();
        AtomicInteger warnings = new AtomicInteger();

        String output = PlaceholderAPIHook.applyPlaceholders(
                "%player_name% has %vault_eco_balance%",
                player,
                name -> true,
                (ignored, text) -> {
                    resolverSawVaultPlaceholder.set(text.contains("%vault_eco_"));
                    return text.replace("%player_name%", "Remy");
                },
                () -> false,
                (message, failure) -> warnings.incrementAndGet(),
                System::nanoTime
        );

        assertEquals("Remy has %vault_eco_balance%", output);
        assertFalse(resolverSawVaultPlaceholder.get());
        assertEquals(1, warnings.get());
    }

    @Test
    void resolvesVaultEconomyPlaceholdersWhenProviderIsAvailable() {
        Player player = player("Remy");

        String output = PlaceholderAPIHook.applyPlaceholders(
                "%player_name% has %vault_eco_balance%",
                player,
                name -> true,
                (ignored, text) -> text
                        .replace("%player_name%", "Remy")
                        .replace("%vault_eco_balance%", "125.00"),
                () -> true,
                (message, failure) -> { },
                System::nanoTime
        );

        assertEquals("Remy has 125.00", output);
    }

    private static String failingResolution(
            String input,
            Player player,
            AtomicInteger warnings,
            AtomicLong now
    ) {
        return PlaceholderAPIHook.applyPlaceholders(
                input,
                player,
                name -> true,
                (ignored, text) -> {
                    throw new IllegalStateException("broken expansion");
                },
                () -> true,
                (message, failure) -> warnings.incrementAndGet(),
                now::get
        );
    }

    private static Player player(String name) {
        UUID uniqueId = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return InterfaceProxy.of(Player.class, Map.of(
                "getName", arguments -> name,
                "getUniqueId", arguments -> uniqueId
        ));
    }
}
