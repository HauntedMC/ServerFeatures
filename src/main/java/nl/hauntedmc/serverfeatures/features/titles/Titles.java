package nl.hauntedmc.serverfeatures.features.titles;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.titles.internal.TitleHandler;
import nl.hauntedmc.serverfeatures.features.titles.listener.PlayerLoginListener;
import nl.hauntedmc.serverfeatures.features.titles.meta.Meta;

import java.util.HashMap;
import java.util.Map;

public class Titles extends BukkitBaseFeature<Meta> {

    private TitleHandler titleHandler;

    public Titles(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Titles feature.
     */
    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("fade-in", 10);
        defaults.put("stay", 30);
        defaults.put("fade-out", 30);
        defaults.put("delay", 15);
        defaults.put("server", "default_server");
        return defaults;
    }

    /**
     * Returns the default messages for the Titles feature.
     */
    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("titles.join_title", "&8&l[&b&lHaunted&6&lMC&8&l]");
        messages.add("titles.join_subtitle", "&7Je bent nu in &6{server}");
        return messages;
    }

    /**
     * Initialize the feature: register the player join listener.
     */
    @Override
    public void initialize() {
        titleHandler = new TitleHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new PlayerLoginListener(this));
    }

    /**
     * Disable the feature: clear titles if necessary.
     */
    @Override
    public void disable() {
        // Optionally, clear titles or cancel scheduled tasks here.
    }

    public TitleHandler getTitleHandler() {
        return titleHandler;
    }
}
