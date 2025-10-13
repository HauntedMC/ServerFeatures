package nl.hauntedmc.serverfeatures.features.actionbar.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ActionbarHandler {

    private final FeatureTaskManager taskManager;
    private final ActionbarRegistry messageRegistry;
    private final int messageInterval;
    private final Actionbar feature;

    // References to the currently running tasks
    private BukkitTask currentRepeatingTask = null;
    private BukkitTask currentDelayTask = null;
    private BukkitTask messageIntervalTask = null;

    private int currentMessageIndex = 0;
    private boolean running = false;

    public ActionbarHandler(Actionbar feature) {
        this.feature = feature;
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
            taskManager.cancelTask(currentRepeatingTask);
        }
        if (currentDelayTask != null && !currentDelayTask.isCancelled()) {
            taskManager.cancelTask(currentDelayTask);
        }
        if (messageIntervalTask != null && !messageIntervalTask.isCancelled()) {
            taskManager.cancelTask(messageIntervalTask);
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

        currentRepeatingTask = taskManager.scheduleRepeatingTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String messageKey = currentMessage.getMessageKey();
                Component message = feature.getLocalizationHandler().getMessage("actionbar." + messageKey).forAudience(player).build();
                player.sendActionBar(message);
            }
        }, BukkitTime.seconds(0), BukkitTime.seconds(1));

        currentDelayTask = taskManager.scheduleDelayedTask(() -> {
            if (currentRepeatingTask != null) {
                taskManager.cancelTask(currentRepeatingTask);
            }

            messageIntervalTask = taskManager.scheduleDelayedTask(() -> {
                // Move to the next message
                currentMessageIndex = (currentMessageIndex + 1) % messageRegistry.getTotalMessages();
                scheduleNextMessage();
            }, BukkitTime.ticks(messageInterval));

        }, BukkitTime.ticks(durationTicks));
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
            }, BukkitTime.seconds(0), BukkitTime.seconds(1));

            taskManager.scheduleDelayedTask(() -> {
                if (manualRepeatingTask != null) {
                    taskManager.cancelTask(manualRepeatingTask);
                }
            }, BukkitTime.ticks(durationTicks));
        }

        if (wasRunning) {
            taskManager.scheduleDelayedTask(this::startMessageCycle, BukkitTime.ticks(durationTicks + 3));
        }
    }

    private void sendActionbar(Player player, String message) {
        message = TextFormatter.convert(message)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .preprocess(s -> {
                    s = PlaceholderAPIHook.applyPlaceholders(s, player);
                    return s;
                })
                .toMiniMessage();

        Component messageComponent = ComponentFormatter.deserialize(message)
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.ALL_DEFAULTS())
                .autoLinkUrls()
                .toComponent();

        player.sendActionBar(messageComponent);
    }

    public boolean messageCycleRunning() {
        return running;
    }
}
