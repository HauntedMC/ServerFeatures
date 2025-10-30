package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
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

        // Per-player update so localization/placeholders are correct
        for (Map.Entry<UUID, BossBar> entry : activeBossbars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            updateBossbar(player, entry.getValue(), currentMessage);
        }

        if (currentMessage.isAutoFade()) {
            startAutoFade(currentMessage);
        }

        taskManager.scheduleDelayedTask(() -> {
            currentMessageIndex = (currentMessageIndex + 1) % Math.max(1, messageRegistry.getTotalMessages());
            scheduleNextMessage();
        }, BukkitTime.ticks(currentMessage.getDurationTicks()));
    }

    private void startAutoFade(BossbarMessage message) {
        ConfigNode anim = feature.getConfigHandler().node("animation");
        int stepsPerSecond = Math.max(1, anim.get("steps_per_second").as(Integer.class, 5));
        int fadeDelay = Math.max(0, anim.get("fade_delay").as(Integer.class, 0));

        long duration = message.getDurationTicks();
        if (duration <= fadeDelay) return;

        long fadeDuration = duration - fadeDelay;
        long seconds = Math.max(1, fadeDuration / 20L);
        int totalSteps = Math.max(1, (int) (seconds * (long) stepsPerSecond));
        long timePerStep = Math.max(1L, fadeDuration / totalSteps);

        taskManager.scheduleDelayedTask(() -> {
            AtomicInteger stepCounter = new AtomicInteger(0);
            AtomicReference<BukkitTask> taskRef = new AtomicReference<>();

            BukkitTask task = taskManager.scheduleRepeatingTask(() -> {
                int currentStep = stepCounter.getAndIncrement();
                double progress = 1.0 - ((double) currentStep / totalSteps);
                for (BossBar bossBar : activeBossbars.values()) {
                    bossBar.progress((float) Math.max(0.0, Math.min(progress, 1.0)));
                }
                if (currentStep >= totalSteps) {
                    taskManager.cancelTask(taskRef.get());
                }
            }, BukkitTime.seconds(0), BukkitTime.ticks(timePerStep));
            taskRef.set(task);
        }, BukkitTime.ticks(fadeDelay));
    }

    private void updateBossbar(@NotNull Player player, @NotNull BossBar bar, @NotNull BossbarMessage message) {
        Component title = getMessageComponent(player, message);

        bar.name(title);
        bar.color(mapColor(message.getColor()));
        bar.overlay(mapOverlay(message.getStyle()));
        bar.progress((float) message.getInitialProgress());

        // Reset Adventure flags to match message flags
        for (BossBar.Flag f : BossBar.Flag.values()) {
            bar.removeFlag(f);
        }
        for (BossBar.Flag f : mapFlags(message.getFlags())) {
            bar.addFlag(f);
        }
    }

    private @NotNull Component getMessageComponent(Player player, BossbarMessage message) {
        return feature.getLocalizationHandler()
                .getMessage("bossbar." + message.getMessageKey())
                .forAudience(player)
                .build();
    }

    public void showBossbar(Player player) {
        BossbarMessage currentMessage = messageRegistry.get(currentMessageIndex);
        Component title = getMessageComponent(player, currentMessage);

        BossBar bar = BossBar.bossBar(
                title,
                (float) currentMessage.getInitialProgress(),
                mapColor(currentMessage.getColor()),
                mapOverlay(currentMessage.getStyle())
        );
        for (BossBar.Flag f : mapFlags(currentMessage.getFlags())) {
            bar.addFlag(f);
        }

        player.showBossBar(bar);
        activeBossbars.put(player.getUniqueId(), bar);
    }

    public void removeBossbar(Player player) {
        BossBar bar = activeBossbars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void initOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showBossbar(player);
        }
    }

    public void removeAllBossbars() {
        for (Map.Entry<UUID, BossBar> e : activeBossbars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        activeBossbars.clear();
    }

    private static BossBar.Color mapColor(BarColor c) {
        return switch (c) {
            case PINK -> BossBar.Color.PINK;
            case BLUE -> BossBar.Color.BLUE;
            case RED -> BossBar.Color.RED;
            case GREEN -> BossBar.Color.GREEN;
            case YELLOW -> BossBar.Color.YELLOW;
            case PURPLE -> BossBar.Color.PURPLE;
            case WHITE -> BossBar.Color.WHITE;
        };
    }

    private static BossBar.Overlay mapOverlay(BarStyle s) {
        return switch (s) {
            case SOLID -> BossBar.Overlay.PROGRESS;
            case SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6;
            case SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10;
            case SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12;
            case SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20;
        };
    }

    private static EnumSet<BossBar.Flag> mapFlags(Iterable<BarFlag> flags) {
        EnumSet<BossBar.Flag> set = EnumSet.noneOf(BossBar.Flag.class);
        if (flags == null) return set;
        for (BarFlag f : flags) {
            switch (f) {
                case CREATE_FOG -> set.add(BossBar.Flag.CREATE_WORLD_FOG);
                case DARKEN_SKY -> set.add(BossBar.Flag.DARKEN_SCREEN);
                case PLAY_BOSS_MUSIC -> set.add(BossBar.Flag.PLAY_BOSS_MUSIC);
            }
        }
        return set;
    }
}
