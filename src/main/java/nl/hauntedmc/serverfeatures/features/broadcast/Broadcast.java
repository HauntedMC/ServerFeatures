package nl.hauntedmc.serverfeatures.features.broadcast;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.broadcast.command.BroadcastCommand;
import nl.hauntedmc.serverfeatures.features.broadcast.meta.Meta;

public class Broadcast extends BukkitBaseFeature<Meta> {

    public Broadcast(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("title_fade_in", 20);
        cfg.put("title_stay", 100);
        cfg.put("title_fade_out",20);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("broadcast.usage",  "&eGebruik: /broadcast <title|chat> <bericht>");
        m.add("broadcast.sent",   "&aBroadcast verstuurd.");
        m.add("broadcast.noMode", "&cOngeldige optie. Gebruik 'title' of 'chat'.");
        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager()
                .getCommandManager()
                .registerFeatureCommand(new BroadcastCommand(this));
    }

    @Override public void disable() {
    }
}
