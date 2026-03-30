package nl.hauntedmc.serverfeatures.features.playerlanguage;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
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
        if (!getLifecycleManager().getDataManager().initDataProvider(getFeatureName())) {
            throw new IllegalStateException("DataProvider is not available for feature '" + getFeatureName() + "'.");
        }
        getLifecycleManager().getDataManager().registerConnection("orm", DatabaseType.MYSQL, "player_data_rw");

        ORMContext orm = getLifecycleManager().getDataManager()
                .createORMContext("orm",
                        nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity.class,
                        nl.hauntedmc.dataregistry.api.entities.PlayerEntity.class)
                .orElseThrow();

        this.service = new LanguageService(orm);

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
