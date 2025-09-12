package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BossbarHandler {

    private final FeatureTaskManager taskManager;
    private final Map<UUID, BossBar> activeBossbars = new ConcurrentHashMap<>();
    private final BossbarRegistry messageRegistry;
    private final Bossbars feature;
    private int currentMessageIndex = 0;

    public BossbarHandler(Bossbars feature) {
        this.feature = feature;
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        this.messageRegistry = new BossbarRegistry(feature);
    }

    public void startMessageCycle() {
        if (messageRegistry.getMessages().isEmpty()) {
            return;
        }
        scheduleNextMessage();
    }

    private void scheduleNextMessage() {
        BossbarMessage currentMessage = messageRegistry.get(currentMessageIndex);
        // Update all active bossbars with the current message
        for (BossBar bossBar : activeBossbars.values()) {
            updateBossbar(bossBar, currentMessage);
        }
        // If autoFade is enabled, schedule a task to gradually reduce the progress over the message duration.
        if (currentMessage.isAutoFade()) {
            startAutoFade(currentMessage);
        }
        // Schedule the next message after the current message’s duration
        taskManager.scheduleDelayedTask(() -> {
            currentMessageIndex = (currentMessageIndex + 1) % messageRegistry.getTotalMessages();
            scheduleNextMessage();
        }, BukkitTime.ticks(currentMessage.getDurationTicks()));
    }

    private void startAutoFade(BossbarMessage message) {
        Object animationSettings = feature.getConfigHandler().getSetting("animation");
        int stepsPerSecond;
        int fadeDelay;
        if (animationSettings instanceof ConfigurationSection configSection) {
            stepsPerSecond = configSection.getInt("steps_per_second", 5);
            fadeDelay = configSection.getInt("fade_delay", 0);
        } else {
            stepsPerSecond = 5;
            fadeDelay = 0;
        }

        long duration = message.getDurationTicks();
        if (duration <= fadeDelay) {
            return;
        }
        long fadeDuration = duration - fadeDelay;
        int seconds = (int) (fadeDuration / 20L);
        int totalSteps = seconds * stepsPerSecond;
        long timePerStep = fadeDuration / totalSteps;

        taskManager.scheduleDelayedTask(() -> {
            AtomicInteger stepCounter = new AtomicInteger(0);
            AtomicReference<BukkitTask> taskRef = new AtomicReference<>();

            BukkitTask task = taskManager.scheduleRepeatingTask(() -> {
                int currentStep = stepCounter.getAndIncrement();
                double progress = 1.0 - ((double) currentStep / totalSteps);
                for (BossBar bossBar : activeBossbars.values()) {
                    bossBar.setProgress(Math.max(0.0, Math.min(progress, 1.0)));
                }
                if (currentStep >= totalSteps) {
                    taskManager.cancelTask(taskRef.get());
                }
            }, BukkitTime.seconds(0), BukkitTime.ticks(timePerStep));
            taskRef.set(task);
        }, BukkitTime.ticks(fadeDelay));
    }

    private void updateBossbar(BossBar bossBar, BossbarMessage message) {
        String messageKey = message.getMessageKey();
        String text = getMessage(bossBar.getPlayers().getFirst(), messageKey);

        bossBar.setTitle(text);
        bossBar.setColor(message.getColor());
        bossBar.setStyle(message.getStyle());
        bossBar.setProgress(message.getInitialProgress());

        for (BarFlag flag : BarFlag.values()) {
            bossBar.removeFlag(flag);
        }
        for (BarFlag flag : message.getFlags()) {
            bossBar.addFlag(flag);
        }
    }

    private @NotNull String getMessage(Player player, String messageKey) {
        Component messageComponent = feature.getLocalizationHandler().getMessage("bossbar." + messageKey).forAudience(player).build();
        String text = LegacyComponentSerializer.legacyAmpersand().serialize(messageComponent);
        return text;
    }

    public void showBossbar(Player player) {
        BossbarMessage currentMessage = messageRegistry.get(currentMessageIndex);
        String messageKey = currentMessage.getMessageKey();
        String text = getMessage(player, messageKey);

        BossBar bossBar = Bukkit.createBossBar(text, currentMessage.getColor(), currentMessage.getStyle());
        bossBar.setProgress(currentMessage.getInitialProgress());
        for (BarFlag flag : currentMessage.getFlags()) {
            bossBar.addFlag(flag);
        }
        bossBar.addPlayer(player);
        activeBossbars.put(player.getUniqueId(), bossBar);
    }

    public void removeBossbar(Player player) {
        BossBar bossBar = activeBossbars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    public void initOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showBossbar(player);
        }
    }

    public void removeAllBossbars() {
        for (BossBar bossBar : activeBossbars.values()) {
            bossBar.removeAll();
        }
        activeBossbars.clear();
    }
}
