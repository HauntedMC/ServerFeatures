package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActionBarsTest {

    @AfterEach
    void cleanup() {
        ActionBars.shutdown();
    }

    @Test
    void returnsNoopServiceWhenNotBootstrapped() {
        ActionBarAPI service = ActionBars.service();
        assertTrue(service instanceof nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl.NoopActionBarAPI);
    }

    @Test
    void bootstrapReplacesGlobalServiceUntilShutdown() {
        ActionBarAPI custom = new TestActionBarApi();
        ActionBars.bootstrap(custom);
        assertSame(custom, ActionBars.service());

        ActionBars.shutdown();
        assertTrue(ActionBars.service() instanceof nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl.NoopActionBarAPI);
    }

    @Test
    void legacyImplementationsReceiveSafeTargetedDefaults() {
        ActionBarAPI legacyImplementation = new TestActionBarApi();
        Player player = mock(Player.class);
        Component message = Component.text("targeted");

        legacyImplementation.sendOnce(player, message);
        legacyImplementation.send(player, message, 3, PauseMode.PAUSE_CYCLE);

        verify(player, times(2)).sendActionBar(message);
    }

    private static final class TestActionBarApi implements ActionBarAPI {
        @Override
        public ActionBarCycleHandle startCycle(ActionBarCycle cycle) {
            return new ActionBarCycleHandle() {
                @Override
                public boolean isActive() {
                    return false;
                }

                @Override
                public void cancel() {
                }
            };
        }

        @Override
        public boolean isCycleRunning() {
            return false;
        }

        @Override
        public void stopCycle() {
        }

        @Override
        public void sendOnceBroadcast(Component component) {
        }

        @Override
        public void sendBroadcast(Component component, int seconds, PauseMode pauseMode) {
        }

        @Override
        public void sendOnceBroadcastPerPlayer(Function<Player, Component> supplier) {
        }

        @Override
        public void sendBroadcastPerPlayer(Function<Player, Component> supplier, int seconds, PauseMode pauseMode) {
        }
    }
}
