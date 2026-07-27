package nl.hauntedmc.serverfeatures.features.scoreboard.internal;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardHandlerTest {

    @Test
    void continuesUpdatingOtherPlayersAfterRuntimeAndLinkageFailures() {
        Player runtimeFailure = player("runtime");
        Player successful = player("successful");
        Player linkageFailure = player("linkage");
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        ScoreboardHandler.updatePlayersIndependently(
                List.of(runtimeFailure, successful, linkageFailure),
                player -> {
                    if (player == runtimeFailure) {
                        throw new IllegalStateException("broken expansion");
                    }
                    if (player == linkageFailure) {
                        throw new NoSuchMethodError("incompatible expansion");
                    }
                    updates.incrementAndGet();
                },
                (player, failure) -> failures.incrementAndGet()
        );

        assertEquals(1, updates.get());
        assertEquals(2, failures.get());
    }

    private static Player player(String name) {
        return InterfaceProxy.of(Player.class, Map.of("getName", arguments -> name));
    }
}
