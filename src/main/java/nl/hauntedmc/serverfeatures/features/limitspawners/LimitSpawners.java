package nl.hauntedmc.serverfeatures.features.limitspawners;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.LimitSpawnersListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.TransformListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta;

public final class LimitSpawners extends BukkitBaseFeature<Meta> {

    private LimitSpawnersHandler handler;

    public LimitSpawners(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("max_spawn", 1);
        cfg.put("remove_mobs_on_chunk_unload", true);
        return cfg;
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
    }

    @Override
    public void disable() {
    }

    public LimitSpawnersHandler getHandler() {
        return handler;
    }
}
