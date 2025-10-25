package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Paper/Spigot implementation. All state changes happen on the main thread.
 */
public final class PaperActionBarAPI implements ActionBarAPI {

    private final Plugin plugin;

    // Cycle state
    private ActionBarCycle currentCycle = null;
    private int cycleIndex = 0;
    private boolean cyclePaused = false;

    // Generation protects handles from cancelling newer cycles
    private final AtomicInteger cycleGen = new AtomicInteger(0);
    private int runningGen = 0;

    // Scheduled task ids
    private int repeatingTaskId = -1;
    private int endOfEntryTaskId = -1;
    private int resumeCycleTaskId = -1;

    // Broadcast state
    private int broadcastRepeatingTaskId = -1;
    private int endBroadcastTaskId = -1;

    public PaperActionBarAPI(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /* ========================= ActionBarAPI ========================= */

    @Override
    public @NotNull ActionBarCycleHandle startCycle(@NotNull ActionBarCycle cycle) {
        runSync(() -> {
            // Replace current cycle
            cancelCycleTasks(); // stop anything existing
            this.currentCycle = cycle;
            this.cycleIndex = 0;
            this.cyclePaused = false;
            this.runningGen = cycleGen.incrementAndGet();
            if (!cycle.entries().isEmpty()) {
                playCurrentEntryThenScheduleNext();
            }
        });

        final int myGen = cycleGen.get();
        return new ActionBarCycleHandle() {
            @Override
            public boolean isActive() {
                return isCycleRunning() && runningGen == myGen;
            }

            @Override
            public void cancel() {
                runSync(() -> {
                    if (runningGen == myGen) {
                        stopCycle(); // only cancel if still the active generation
                    }
                });
            }
        };
    }

    @Override
    public boolean isCycleRunning() {
        return currentCycle != null && !currentCycle.entries().isEmpty();
    }

    @Override
    public void stopCycle() {
        runSync(() -> {
            cancelCycleTasks();
            currentCycle = null;
            cycleIndex = 0;
            cyclePaused = false;
        });
    }

    @Override
    public void sendOnceBroadcast(@NotNull Component component) {
        runSync(() -> sendActionBarAll(component));
    }

    @Override
    public void sendBroadcast(@NotNull Component component, int seconds, @NotNull PauseMode mode) {
        runSync(() -> {
            if (seconds <= 0) {
                sendActionBarAll(component);
                return;
            }
            if (mode == PauseMode.PAUSE_CYCLE && isCycleRunning() && !cyclePaused) {
                pauseCycle();
            }
            startBroadcastRepeating(() -> sendActionBarAll(component), seconds);
        });
    }

    @Override
    public void sendOnceBroadcastPerPlayer(@NotNull Function<Player, Component> supplier) {
        runSync(() -> sendActionBarAllPerPlayer(supplier));
    }

    @Override
    public void sendBroadcastPerPlayer(@NotNull Function<Player, Component> supplier, int seconds, @NotNull PauseMode mode) {
        runSync(() -> {
            if (seconds <= 0) {
                sendActionBarAllPerPlayer(supplier);
                return;
            }
            if (mode == PauseMode.PAUSE_CYCLE && isCycleRunning() && !cyclePaused) {
                pauseCycle();
            }
            startBroadcastRepeating(() -> sendActionBarAllPerPlayer(supplier), seconds);
        });
    }

    /* ============================== Internals ============================== */

    private void playCurrentEntryThenScheduleNext() {
        // Assumes main thread and currentCycle != null
        List<ActionBarEntry> entries = currentCycle.entries();
        if (entries.isEmpty()) return;

        ActionBarEntry entry = entries.get(Math.floorMod(cycleIndex, entries.size()));

        // Repeat send every 20 ticks for entry.seconds()
        cancelTask(repeatingTaskId);
        repeatingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entry.isPerPlayer()) {
                sendActionBarAllPerPlayer(entry.perPlayer());
            } else {
                sendActionBarAll(entry.component());
            }
        }, 0L, 20L);

        // End-of-entry => schedule gap then advance
        cancelTask(endOfEntryTaskId);
        int durationTicks = Math.max(0, entry.seconds()) * 20;
        endOfEntryTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cancelTask(repeatingTaskId); // stop sending this entry
            repeatingTaskId = -1;

            int gapSeconds = (currentCycle != null) ? currentCycle.gapSeconds() : 0;
            int gapTicks = Math.max(0, gapSeconds) * 20;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                cycleIndex++;
                if (currentCycle != null && !cyclePaused) {
                    playCurrentEntryThenScheduleNext();
                }
            }, gapTicks);
        }, durationTicks);
    }

    private void pauseCycle() {
        cyclePaused = true;
        cancelTask(repeatingTaskId);
        cancelTask(endOfEntryTaskId);
        repeatingTaskId = -1;
        endOfEntryTaskId = -1;
    }

    private void resumeCycle() {
        if (currentCycle == null || currentCycle.entries().isEmpty()) return;
        if (!cyclePaused) return;
        cyclePaused = false;
        playCurrentEntryThenScheduleNext();
    }

    private void startBroadcastRepeating(Runnable broadcastTick, int seconds) {
        // Replace any existing timed broadcast
        cancelTask(broadcastRepeatingTaskId);
        cancelTask(endBroadcastTaskId);

        broadcastRepeatingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, broadcastTick, 0L, 20L);
        endBroadcastTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cancelTask(broadcastRepeatingTaskId);
            broadcastRepeatingTaskId = -1;
            // If cycle was paused for this broadcast, resume now.
            if (cyclePaused) {
                cancelTask(resumeCycleTaskId);
                resumeCycleTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::resumeCycle, 1L);
            }
        }, Math.max(0, seconds) * 20L);
    }

    private void cancelCycleTasks() {
        cancelTask(repeatingTaskId);
        cancelTask(endOfEntryTaskId);
        cancelTask(resumeCycleTaskId);
        repeatingTaskId = -1;
        endOfEntryTaskId = -1;
        resumeCycleTaskId = -1;
    }

    private void cancelTask(int taskId) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void sendActionBarAll(Component c) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(c);
    }

    private void sendActionBarAllPerPlayer(@NotNull java.util.function.Function<Player, Component> fn) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Component c = fn.apply(p);
            if (c != null) p.sendActionBar(c);
        }
    }

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /**
     * Optional: call this on plugin disable before ActionBars.shutdown().
     */
    public void shutdown() {
        runSync(() -> {
            cancelCycleTasks();
            cancelTask(broadcastRepeatingTaskId);
            cancelTask(endBroadcastTaskId);
            broadcastRepeatingTaskId = -1;
            endBroadcastTaskId = -1;
            currentCycle = null;
            cycleIndex = 0;
            cyclePaused = false;
        });
    }
}
