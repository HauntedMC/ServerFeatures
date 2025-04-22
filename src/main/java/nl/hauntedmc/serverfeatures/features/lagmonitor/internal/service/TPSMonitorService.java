package nl.hauntedmc.serverfeatures.features.lagmonitor.internal.service;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.lagmonitor.LagMonitor;
import org.bukkit.Bukkit;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class TPSMonitorService {

    private final int CHECK_INTERVAL;
    private final int MONITOR_DURATION;
    private final int ALERT_INTERVAL;
    private final int TPS_CHECKER_INTERVAL;
    private final double TPS_THRESHOLD;

    private final ArrayDeque<Double> tpsHistory = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long lastDiscordAlert = 0;

    private final DiscordService discordService;
    private final LagMonitor feature;

    public TPSMonitorService(LagMonitor feature, DiscordService discordService) {
        this.feature = feature;
        this.discordService = discordService;

        this.CHECK_INTERVAL = (int) feature.getConfigHandler().getSetting("tps_check_interval");
        this.MONITOR_DURATION = (int) feature.getConfigHandler().getSetting("tps_monitor_duration");
        this.ALERT_INTERVAL = (int) feature.getConfigHandler().getSetting("tps_alert_interval");
        this.TPS_THRESHOLD = (double) feature.getConfigHandler().getSetting("tps_threshold");
        this.TPS_CHECKER_INTERVAL = (int) feature.getConfigHandler().getSetting("tps_checker_interval");
    }

    public void start() {
        startTPSLogger();
        startTPSChecker();
    }

    private void startTPSLogger() {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(() -> {
            double tps = Bukkit.getServer().getTPS()[0]; // Fetch the 1-minute TPS average
            lock.lock();
            try {
                if (tpsHistory.size() >= MONITOR_DURATION / CHECK_INTERVAL) {
                    tpsHistory.pollFirst(); // Remove oldest value
                }
                tpsHistory.addLast(tps);
            } finally {
                lock.unlock();
            }
        }, 0, CHECK_INTERVAL * 20L);
    }

    private void startTPSChecker() {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(() -> {
            double avgTps = calculateAverageTPS();
            if (avgTps < TPS_THRESHOLD) {
                String formattedTPS = String.format("%.2f", avgTps);
                notifyStaff(formattedTPS);
                long now = System.currentTimeMillis();
                if (now - lastDiscordAlert > ALERT_INTERVAL * 1000L) {
                    sendDiscordAlert(formattedTPS);
                    lastDiscordAlert = now;
                }
            }
        }, 0, TPS_CHECKER_INTERVAL * 20L);
    }

    private double calculateAverageTPS() {
        lock.lock();
        try {
            if (tpsHistory.isEmpty()) return 20.0;
            return tpsHistory.stream().mapToDouble(Double::doubleValue).average().orElse(20.0);
        } finally {
            lock.unlock();
        }
    }

    private void notifyStaff(String avgTps) {
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        Component lagMessage = feature.getLocalizationHandler().getMessage("lagmonitor.notify_lag").withPlaceholders(Map.of("tps", avgTps, "server", serverName)).build();
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("serverfeatures.feature.lagmonitor.notify"))
                .forEach(player -> player.sendMessage(lagMessage));
    }

    private void sendDiscordAlert(String avgTps) {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask( () -> discordService.sendNotification(avgTps));
    }

}
