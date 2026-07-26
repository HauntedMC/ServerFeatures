package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBarCycle;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBarEntry;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.PauseMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NoopActionBarAPITest {

    @Test
    void noOpImplementationIsSafeAndAlwaysInactive() {
        NoopActionBarAPI api = NoopActionBarAPI.INSTANCE;
        var handle = api.startCycle(ActionBarCycle.builder().add(ActionBarEntry.of(Component.text("x"), 1)).build());

        assertNotNull(handle);
        assertFalse(handle.isActive());
        assertFalse(api.isCycleRunning());

        handle.cancel();
        api.stopCycle();
        api.sendOnceBroadcast(Component.text("x"));
        api.sendBroadcast(Component.text("x"), 1, PauseMode.NONE);
        api.sendOnceBroadcastPerPlayer(player -> Component.text("x"));
        api.sendBroadcastPerPlayer(player -> Component.text("x"), 1, PauseMode.PAUSE_CYCLE);

        assertFalse(handle.isActive());
        assertSame(NoopActionBarAPI.INSTANCE, api);
        assertFalse(api.isCycleRunning());
    }
}
