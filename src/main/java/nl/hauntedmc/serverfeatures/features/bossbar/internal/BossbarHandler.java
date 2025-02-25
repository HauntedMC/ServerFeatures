package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        }, currentMessage.getDurationTicks());
    }

    private void startAutoFade(BossbarMessage message) {
        Object animationSettings = feature.getConfigHandler().getSetting("animation");
        int steps = 20;
        if (animationSettings instanceof ConfigurationSection configSection) {
            steps = configSection.getInt("steps", 20);
        }
        long duration = message.getDurationTicks();
        long interval = duration / steps;
        for (int i = 0; i <= steps; i++) {
            final double progress = 1.0 - ((double) i / steps);
            taskManager.scheduleDelayedTask(() -> {
                for (BossBar bossBar : activeBossbars.values()) {
                    bossBar.setProgress(Math.max(0.0, Math.min(progress, 1.0)));
                }
            }, i * interval);
        }
    }

    private void updateBossbar(BossBar bossBar, BossbarMessage message) {
        bossBar.setTitle(message.getText());
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

    public void showBossbar(Player player) {
        BossbarMessage currentMessage = messageRegistry.get(currentMessageIndex);
        BossBar bossBar = Bukkit.createBossBar(currentMessage.getText(), currentMessage.getColor(), currentMessage.getStyle());
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
