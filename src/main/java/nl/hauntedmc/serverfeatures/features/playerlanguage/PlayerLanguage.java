package nl.hauntedmc.serverfeatures.features.playerlanguage;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.APIRegistry;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.serverfeatures.features.playerlanguage.listener.LanguageListener;
import nl.hauntedmc.serverfeatures.features.playerlanguage.meta.Meta;
import nl.hauntedmc.serverfeatures.features.playerlanguage.service.LanguageService;

public class PlayerLanguage extends BukkitBaseFeature<Meta> {

    private LanguageService service;

    public PlayerLanguage(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        DataRegistry dataRegistry = getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for PlayerLanguage."));
        if (!dataRegistry.isFeatureEnabled(DataRegistryFeature.LANGUAGE)) {
            throw new IllegalStateException("DataRegistry feature 'language' must be enabled for PlayerLanguage.");
        }
        this.service = new LanguageService(this, dataRegistry);

        getLifecycleManager().getListenerManager().registerListener(new LanguageListener(this));

        APIRegistry.register(LanguageAPI.class, service);
    }

    @Override
    public void disable() {
        APIRegistry.unregister(LanguageAPI.class);
    }

    public LanguageService getService() {
        return service;
    }
}
