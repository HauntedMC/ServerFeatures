package nl.hauntedmc.serverfeatures.features.betterdoors;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.betterdoors.internal.BetterDoorsHandler;
import nl.hauntedmc.serverfeatures.features.betterdoors.listener.BetterDoorsListener;
import nl.hauntedmc.serverfeatures.features.betterdoors.meta.Meta;

public final class BetterDoors extends BukkitBaseFeature<Meta> {

    private BetterDoorsHandler handler;

    public BetterDoors(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);

        // Knock config (wood doors)
        cfg.put("knock_wood_volume", 1.0D);
        cfg.put("knock_wood_pitch", 1.0D);

        // Knock config (non-wood doors: iron, copper variants, etc.)
        cfg.put("knock_other_volume", 1.0D);
        cfg.put("knock_other_pitch", 1.0D);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.handler = new BetterDoorsHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new BetterDoorsListener(handler));
    }

    @Override
    public void disable() {
    }

    public BetterDoorsHandler getHandler() {
        return handler;
    }
}
