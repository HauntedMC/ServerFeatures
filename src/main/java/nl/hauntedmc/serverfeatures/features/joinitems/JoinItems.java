package nl.hauntedmc.serverfeatures.features.joinitems;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.joinitems.internal.JoinItemsHandler;
import nl.hauntedmc.serverfeatures.features.joinitems.listener.JoinItemsListener;
import nl.hauntedmc.serverfeatures.features.joinitems.meta.Meta;

/**
 * JoinItems feature:
 * - Gives configured items at fixed inventory slots after join (with a delay).
 * - Items are tagged via PDC for robust identification.
 * - Enforces immovable/undroppable/etc. protections and executes commands on click.
 */
public final class JoinItems extends BukkitBaseFeature<Meta> {

    private JoinItemsHandler handler;

    public JoinItems(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("include-all-items", false);
        cfg.put("remove-on-join", true);
        cfg.put("remove-on-leave", true);
        cfg.put("join-delay", 2); // ticks
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.handler = new JoinItemsHandler(this);
        this.handler.reloadFromConfig();
        getLifecycleManager().getListenerManager().registerListener(new JoinItemsListener(this, handler));
    }

    @Override
    public void disable() {
    }

    public JoinItemsHandler getHandler() {
        return handler;
    }
}
