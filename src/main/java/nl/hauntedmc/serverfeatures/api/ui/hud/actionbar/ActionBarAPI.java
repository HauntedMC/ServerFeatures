package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface ActionBarAPI {
    // Cycle (global)
    @NotNull ActionBarCycleHandle startCycle(@NotNull ActionBarCycle cycle);
    boolean isCycleRunning();
    void stopCycle();

    // Broadcasts — static component
    void sendOnceBroadcast(@NotNull Component component);
    void sendBroadcast(@NotNull Component component, int seconds, @NotNull PauseMode pauseMode);

    // Broadcasts — per-player supplier (lets you do i18n and PAPI at call sites)
    void sendOnceBroadcastPerPlayer(@NotNull Function<Player, Component> supplier);
    void sendBroadcastPerPlayer(@NotNull Function<Player, Component> supplier, int seconds, @NotNull PauseMode pauseMode);
}
