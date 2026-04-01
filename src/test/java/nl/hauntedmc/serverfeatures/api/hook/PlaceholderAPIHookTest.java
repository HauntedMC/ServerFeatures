package nl.hauntedmc.serverfeatures.api.hook;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderAPIHookTest {

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
        Player player = InterfaceProxy.of(Player.class, Map.of());
        AtomicBoolean resolverCalled = new AtomicBoolean(false);

        String output = PlaceholderAPIHook.applyPlaceholders(
                "hello",
                player,
                name -> false,
                (p, text) -> {
                    resolverCalled.set(true);
                    return "changed";
                }
        );

        assertEquals("hello", output);
        assertFalse(resolverCalled.get());
    }

    @Test
    void appliesResolverWhenPluginIsEnabledAndPlayerExists() {
        Player player = InterfaceProxy.of(Player.class, Map.of("getName", args -> "Remy"));

        String output = PlaceholderAPIHook.applyPlaceholders(
                "hello %player%",
                player,
                name -> "PlaceholderAPI".equals(name),
                (p, text) -> text.replace("%player%", p.getName())
        );

        assertEquals("hello Remy", output);
        assertTrue(output.contains("Remy"));
    }
}
