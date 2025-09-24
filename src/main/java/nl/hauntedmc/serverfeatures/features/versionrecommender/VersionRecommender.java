package nl.hauntedmc.serverfeatures.features.versionrecommender;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.versionrecommender.internal.RecommendationService;
import nl.hauntedmc.serverfeatures.features.versionrecommender.internal.ViaVersionAdapter;
import nl.hauntedmc.serverfeatures.features.versionrecommender.listener.VersionRecommenderListener;
import nl.hauntedmc.serverfeatures.features.versionrecommender.meta.Meta;

/**
 * Recommends a Minecraft client version based on the server's native protocol
 * (queried via ViaVersion). Configuration is read in the constructors of the
 * classes that need it, not here.
 */
public class VersionRecommender extends BukkitBaseFeature<Meta> {

    public VersionRecommender(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("warn_players_older", true);
        defaults.put("warn_players_newer", true);
        defaults.put("delay_seconds", 10);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("versionrecommender.warn-older",
                "&cLet op: &7Je speelt nu op versie &e{version}&7. &7Voor de beste speelervaring raden wij versie &a{server}&7 aan.");
        m.add("versionrecommender.warn-newer",
                "&cLet op: &7Je speelt nu op versie &e{version}&7. &7Voor de beste speelervaring raden wij versie &a{server}&7 aan. De server zal binnenkort updated worden naar de nieuwste versie.");
        return m;
    }

    @Override
    public void initialize() {
        final ViaVersionAdapter viaAdapter = new ViaVersionAdapter();
        if (!viaAdapter.isAvailable()) {
            getPlugin().getLogger().warning("ViaVersion is not available; feature remains inactive.");
            return;
        }
        final RecommendationService service = new RecommendationService(this, viaAdapter);
        getLifecycleManager().getListenerManager().registerListener(new VersionRecommenderListener(this, service));
    }

    @Override
    public void disable() {
    }
}
