package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBarAPI;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBarCycle;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBarCycleHandle;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.PauseMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class NoopActionBarAPI implements ActionBarAPI {
    public static final NoopActionBarAPI INSTANCE = new NoopActionBarAPI();
    private static final ActionBarCycleHandle NOOP_HANDLE = new ActionBarCycleHandle() {
        @Override public boolean isActive() { return false; }
        @Override public void cancel() { /* no-op */ }
    };
    private NoopActionBarAPI() {}

    @Override public @NotNull ActionBarCycleHandle startCycle(@NotNull ActionBarCycle cycle) { return NOOP_HANDLE; }
    @Override public boolean isCycleRunning() { return false; }
    @Override public void stopCycle() {}

    @Override public void sendOnceBroadcast(@NotNull Component component) {}
    @Override public void sendBroadcast(@NotNull Component component, int seconds, @NotNull PauseMode pauseMode) {}

    @Override public void sendOnceBroadcastPerPlayer(@NotNull Function<Player, Component> supplier) {}
    @Override public void sendBroadcastPerPlayer(@NotNull Function<Player, Component> supplier, int seconds, @NotNull PauseMode pauseMode) {}
}
