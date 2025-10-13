package nl.hauntedmc.serverfeatures.features.lagmonitor;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.lagmonitor.internal.LagMonitorHandler;
import nl.hauntedmc.serverfeatures.features.lagmonitor.meta.Meta;

public class LagMonitor extends BukkitBaseFeature<Meta> {

    private LagMonitorHandler lagMonitorHandler;

    public LagMonitor(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("tps_check_interval", 5);
        defaults.put("tps_monitor_duration", 120);
        defaults.put("tps_alert_interval", 600);
        defaults.put("tps_threshold", 17.0);
        defaults.put("tps_checker_interval", 60);
        defaults.put("discordWebhookURL", "https://discordhook.url");
        return defaults;
    }
    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("lagmonitor.notify_lag", "&c[LagMonitor] &eLag gedetecteerd op {server} ({tps} TPS)");
        return messageMap;
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void initialize() {
        lagMonitorHandler = new LagMonitorHandler(this);
        lagMonitorHandler.startMonitor();
    }

    public void disable() {
    }

    public LagMonitorHandler getLagMonitorHandler() {
        return lagMonitorHandler;
    }
}
