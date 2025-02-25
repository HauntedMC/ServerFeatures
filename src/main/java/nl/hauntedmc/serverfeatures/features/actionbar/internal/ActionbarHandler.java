package nl.hauntedmc.serverfeatures.features.actionbar.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.util.TextUtils;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ActionbarHandler {

    private final FeatureTaskManager taskManager;
    private final ActionbarRegistry messageRegistry;
    private final int messageInterval;

    // References to the currently running tasks
    private BukkitTask currentRepeatingTask = null;
    private BukkitTask currentDelayTask = null;
    private BukkitTask messageIntervalTask = null;

    private int currentMessageIndex = 0;
    private boolean running = false;

    public ActionbarHandler(Actionbar feature) {
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        this.messageRegistry = new ActionbarRegistry(feature);
        this.messageInterval = (int) feature.getConfigHandler().getSetting("message_interval");
    }

    public void startMessageCycle() {
        if (running) {
            return;
        }
        if (messageRegistry.getMessages().isEmpty()) {
            return;
        }
        running = true;
        scheduleNextMessage();
    }

    public void stopMessageCycle() {
        // Cancel all tasks related to the cycle
        if (currentRepeatingTask != null && !currentRepeatingTask.isCancelled()) {
            currentRepeatingTask.cancel();
        }
        if (currentDelayTask != null && !currentDelayTask.isCancelled()) {
            currentDelayTask.cancel();
        }
        if (messageIntervalTask != null && !messageIntervalTask.isCancelled()) {
            messageIntervalTask.cancel();
        }

        // Reset references
        currentRepeatingTask = null;
        currentDelayTask = null;
        messageIntervalTask = null;
        running = false;
    }

    private void scheduleNextMessage() {
        ActionbarMessage currentMessage = messageRegistry.get(currentMessageIndex);
        long durationTicks = currentMessage.getDuration();

        // 1) Start repeating: re-send the same action bar until we’re done
        currentRepeatingTask = taskManager.scheduleRepeatingTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendActionbar(player, currentMessage.getText());
            }
        }, 20L);

        // 2) After 'durationTicks', cancel the repeating task
        //    Then wait for 'messageInterval' ticks before next message
        currentDelayTask = taskManager.scheduleDelayedTask(() -> {
            if (currentRepeatingTask != null) {
                currentRepeatingTask.cancel();
            }

            // Start a gap/wait if you want the screen to be cleared
            // for 'messageInterval' ticks before the next message.
            messageIntervalTask = taskManager.scheduleDelayedTask(() -> {
                // Move to the next message
                currentMessageIndex = (currentMessageIndex + 1) % messageRegistry.getTotalMessages();
                scheduleNextMessage();
            }, messageInterval);

        }, durationTicks);
    }

    private void sendActionbar(Player player, String message) {
        message = TextUtils.parseWithPAPI(message, player);
        message = TextUtils.parseLegacyColors(message);
        Component messageComponent = TextUtils.serializeComponent(message);
        player.sendActionBar(messageComponent);
    }

    public void sendManualActionbar(String text, int timeSeconds) {
        boolean wasRunning = running;
        stopMessageCycle();

        long durationTicks = timeSeconds * 20L;

        if (timeSeconds <= 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendActionbar(player, text);
            }
        } else {
            BukkitTask manualRepeatingTask = taskManager.scheduleRepeatingTask(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendActionbar(player, text);
                }
            }, 20L);

            taskManager.scheduleDelayedTask(() -> {
                if (manualRepeatingTask != null) {
                    manualRepeatingTask.cancel();
                }
            }, durationTicks);
        }

        if (wasRunning) {
            taskManager.scheduleDelayedTask(this::startMessageCycle, durationTicks + 3 * 20L);
        }
    }

    public boolean messageCycleRunning() {
        return running;
    }
}
