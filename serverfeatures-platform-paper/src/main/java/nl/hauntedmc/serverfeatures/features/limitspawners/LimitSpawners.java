package nl.hauntedmc.serverfeatures.features.limitspawners;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.LimitSpawnersListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.TransformListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta;

public final class LimitSpawners extends BukkitBaseFeature<Meta> {

    private LimitSpawnersHandler handler;

    public LimitSpawners(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);
        config.put("max_spawn", 1);
        config.put("save_interval_ticks", 100);
        config.put("reconcile_interval_ticks", 200);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.handler = new LimitSpawnersHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new TransformListener(handler));
        getLifecycleManager().getListenerManager().registerListener(new LimitSpawnersListener(this, handler));
        handler.start();
    }

    @Override
    public void disable() {
        if (handler != null) {
            handler.shutdown();
        }
    }

    public LimitSpawnersHandler getHandler() {
        return handler;
    }
}
