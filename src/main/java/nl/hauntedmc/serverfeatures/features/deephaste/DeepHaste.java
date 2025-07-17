package nl.hauntedmc.serverfeatures.features.deephaste;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.deephaste.listener.BeaconEffectListener;
import nl.hauntedmc.serverfeatures.features.deephaste.meta.Meta;
import org.bukkit.Bukkit;

public class DeepHaste extends BukkitBaseFeature<Meta> {

    public DeepHaste(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("y_level", 6);            // Below this y-level we boost the haste
        defaults.put("haste_amplifier", 7);    // Amplifier for the FAST_DIGGING effect
        return defaults;
    }

    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void initialize() {
        // Make sure we are running on Paper, since BeaconEffectEvent is Paper-only
        if (!isPaperServer()) {
            getLogger().warning("DeepHaste feature requires Paper (BeaconEffectEvent). Disabling this feature.");
            return;
        }
        getLifecycleManager().getListenerManager().registerListener(new BeaconEffectListener(this));
    }

    @Override
    public void disable() {
    }

    /**
     * Utility to detect if the server is Paper.
     */
    private boolean isPaperServer() {
        return Bukkit.getServer().getName().equalsIgnoreCase("Paper")
                || Bukkit.getServer().getVersion().contains("Paper");
    }

}
