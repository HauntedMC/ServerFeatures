package nl.hauntedmc.serverfeatures.features.autolapis;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.autolapis.internal.AutoLapisHandler;
import nl.hauntedmc.serverfeatures.features.autolapis.listener.AutoLapisListener;
import nl.hauntedmc.serverfeatures.features.autolapis.meta.Meta;

public final class AutoLapis extends BukkitBaseFeature<Meta> {

    private AutoLapisHandler handler;

    public AutoLapis(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("stack_size", 1);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap(); // No messages needed for this simple perk
    }


    @Override
    public void initialize() {
        this.handler = new AutoLapisHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new AutoLapisListener(this, handler));
    }

    @Override
    public void disable() {
    }

    public AutoLapisHandler getHandler() {
        return handler;
    }
}
