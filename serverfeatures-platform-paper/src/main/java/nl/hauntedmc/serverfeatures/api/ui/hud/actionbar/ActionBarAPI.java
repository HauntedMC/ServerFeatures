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

    // Targeted delivery. PAUSE_CYCLE suppresses the shared cycle only for this player.
    default void sendOnce(@NotNull Player player, @NotNull Component component) {
        player.sendActionBar(component);
    }

    default void send(@NotNull Player player,
                      @NotNull Component component,
                      int seconds,
                      @NotNull PauseMode pauseMode) {
        sendOnce(player, component);
    }

    /**
     * Starts a targeted override owned by one logical producer.
     *
     * <p>Implementations should use the owner to ensure a later cleanup request cannot remove a
     * newer override from another producer. The default delegates to the legacy targeted send.</p>
     */
    default void sendOverride(@NotNull Player player,
                              @NotNull Component component,
                              int seconds,
                              @NotNull PauseMode pauseMode,
                              @NotNull String owner) {
        send(player, component, seconds, pauseMode);
    }

    /**
     * Clears the active targeted override only when it is still owned by the supplied producer.
     */
    default void clearOverride(@NotNull Player player, @NotNull String owner) {
        // Ownership cannot be verified by the compatibility implementation.
    }

    // Broadcasts — static component
    void sendOnceBroadcast(@NotNull Component component);

    void sendBroadcast(@NotNull Component component, int seconds, @NotNull PauseMode pauseMode);

    // Broadcasts — per-player supplier (lets you do i18n and PAPI at call sites)
    void sendOnceBroadcastPerPlayer(@NotNull Function<Player, Component> supplier);

    void sendBroadcastPerPlayer(@NotNull Function<Player, Component> supplier,
                                int seconds,
                                @NotNull PauseMode pauseMode);
}
