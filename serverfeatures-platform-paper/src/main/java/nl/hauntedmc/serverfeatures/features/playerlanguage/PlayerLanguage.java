package nl.hauntedmc.serverfeatures.features.playerlanguage;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.serverfeatures.features.playerlanguage.listener.LanguageListener;
import nl.hauntedmc.serverfeatures.features.playerlanguage.meta.Meta;
import nl.hauntedmc.serverfeatures.features.playerlanguage.service.LanguageService;
import org.bukkit.entity.Player;

public class PlayerLanguage extends BukkitBaseFeature<Meta> {

    private LanguageService service;

    public PlayerLanguage(FeatureContext<Meta> context) {
        super(context);
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
        DataRegistryApi dataRegistry = getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for PlayerLanguage."));
        if (!dataRegistry.supports(DataRegistryFeature.LANGUAGE)) {
            throw new IllegalStateException("DataRegistry feature 'language' must be enabled for PlayerLanguage.");
        }
        this.service = new LanguageService(this, dataRegistry);

        getLifecycleManager().getListenerManager().registerListener(new LanguageListener(this));

        getLifecycleManager().getApiManager().registerService(LanguageAPI.class, service);

        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            initializePlayer(player);
        }
    }

    @Override
    public void disable() {
    }

    public LanguageService getService() {
        return service;
    }

    public void initializePlayer(Player player) {
        service.warm(player.getUniqueId());
    }
}
