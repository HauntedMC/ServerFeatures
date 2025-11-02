package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

public class FeatureConfigHandler extends ConfigView {

    private final String featureName;
    private final MainConfigHandler main; // optional reference if you pass it

    /** Preferred: share the YamlFile from MainConfigHandler (same lock & memory). */
    public FeatureConfigHandler(MainConfigHandler main, String featureName) {
        super(main.file, "features." + featureName);
        this.featureName = featureName;
        this.main = main;
    }

    /** Convenience if you don't hold a MainConfigHandler instance. */
    public FeatureConfigHandler(ServerFeatures plugin, String featureName) {
        super(new ConfigService(plugin).open("config.yml", true), "features." + featureName);
        this.featureName = featureName;
        this.main = null;
    }

    public void reloadConfig() { file.reload(); }

    public String featureName() { return featureName; }

    // ---- Global access (no need to depend on MainConfigHandler) ----
    public ConfigView globals() { return super.globals(); }
    public Object getGlobalSetting(String key) { return globals().get(key); }
    public <T> T getGlobalSetting(String key, Class<T> type) { return globals().get(key, type); }
    public <T> T getGlobalSetting(String key, Class<T> type, T def) { return globals().get(key, type, def); }
    public ConfigNode globalNode(String key) { return globals().node(key); }

    // Optional convenience for writes:
    public void putGlobal(String dottedPath, Object value) { globals().put(dottedPath, value); }
    public void removeGlobal(String dottedPath) { globals().remove(dottedPath); }
}
