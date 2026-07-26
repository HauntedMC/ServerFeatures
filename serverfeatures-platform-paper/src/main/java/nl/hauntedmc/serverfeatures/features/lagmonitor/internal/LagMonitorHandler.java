package nl.hauntedmc.serverfeatures.features.lagmonitor.internal;

import nl.hauntedmc.serverfeatures.features.lagmonitor.LagMonitor;
import nl.hauntedmc.serverfeatures.features.lagmonitor.internal.service.DiscordService;
import nl.hauntedmc.serverfeatures.features.lagmonitor.internal.service.TPSMonitorService;

public class LagMonitorHandler {

    private final TPSMonitorService TPSMonitorService;

    public LagMonitorHandler(LagMonitor feature) {
        DiscordService discordService = new DiscordService(feature);
        this.TPSMonitorService = new TPSMonitorService(feature, discordService);

    }

    public void startMonitor() {
        this.TPSMonitorService.start();
    }
}
