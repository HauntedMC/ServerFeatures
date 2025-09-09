package nl.hauntedmc.serverfeatures.features.sanitize;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.SanitizeService;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl.CacheSanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl.VersionsSanitizeTask;
import nl.hauntedmc.serverfeatures.features.sanitize.meta.Meta;

public class Sanitize extends BukkitBaseFeature<Meta> {

    private SanitizeService service;

    public Sanitize(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("clean_cache_on_startup", true);
        cfg.put("clean_versions_on_startup", true);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.service = new SanitizeService(this);

        // Register tasks (extensible)
        if (getBoolean("clean_cache_on_startup", false)) {
            service.addTask(new CacheSanitizeTask());
        }
        if (getBoolean("clean_versions_on_startup", false)) {
            service.addTask(new VersionsSanitizeTask());
        }

        // Kick off the sanitize pass on startup — run async to avoid ticking the main thread
        getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            try {
                service.runStartupSanitize();
            } catch (Throwable t) {
                getLogger().warning("[Sanitize] Startup sanitize failed: " + t.getMessage());
            }
        });
    }

    @Override
    public void disable() {
        // Nothing to cancel; sanitize runs once at startup
        this.service = null;
    }

    private boolean getBoolean(String key, boolean def) {
        Object v = getConfigHandler().getSetting(key);
        return (v instanceof Boolean b) ? b : def;
    }
}
