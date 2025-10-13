package nl.hauntedmc.serverfeatures.features.enderframe;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.enderframe.listener.BlockBreakListener;
import nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta;
import org.bukkit.Bukkit;

public class EnderFrame extends BukkitBaseFeature<Meta> {

    private boolean griefPreventionEnabled;

    public EnderFrame(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Provide default settings for this feature.
     */
    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("pickup_radius", 5);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("enderframe.pickup_success", "&aJe hebt een Ender Frame opgepakt!");
        messageMap.add("enderframe.claim_restricted", "&cJe kunt de Ender Frame niet oppakken in andermans claim.");
        return messageMap;
    }

    /**
     * Called when the feature is enabled. Register your listener(s) here.
     */
    @Override
    public void initialize() {
        griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        getLifecycleManager().getListenerManager().registerListener(new BlockBreakListener(this));
    }

    @Override
    public void disable() {
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

}
