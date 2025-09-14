package nl.hauntedmc.serverfeatures.features.afk;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.util.APIRegistry;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.afk.command.AfkCommand;
import nl.hauntedmc.serverfeatures.features.afk.internal.AfkAPI;
import nl.hauntedmc.serverfeatures.features.afk.internal.AfkPlaceholder;
import nl.hauntedmc.serverfeatures.features.afk.internal.AfkService;
import nl.hauntedmc.serverfeatures.features.afk.listener.ActivityListener;
import nl.hauntedmc.serverfeatures.features.afk.meta.Meta;

public class AFK extends BukkitBaseFeature<Meta> {

    private AfkService service;
    private AfkAPI api;

    public AFK(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    public AfkService getService() { return service; }
    public AfkAPI getApi() { return api; }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);

        cfg.put("afk_timeout_seconds", 600);
        cfg.put("movement_distance_threshold", 0.15D);
        cfg.put("rotation_threshold_degrees", 10.0F);

        cfg.put("broadcast_on_state_change", false);

        cfg.put("kick_enabled", true);
        cfg.put("kick_timeout_seconds", 3600);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("afk.enabled_self", "&7[&cAFK&7] &fJe staat nu op AFK.");
        m.add("afk.disabled_self", "&7[&cAFK&7] &fJe bent niet langer AFK.");
        m.add("afk.broadcast_enabled", "&7[&cAFK&7] &e{name}&f staat nu op AFK.");
        m.add("afk.broadcast_disabled", "&7[&cAFK&7] &e{name}&f is niet langer AFK.");
        m.add("afk.kicked", "&cJe bent gekickt omdat je te lang AFK was.");
        m.add("afk.usage", "&eGebruik: /afk");
        m.add("afk.placeholder.afk", "&6● ");
        m.add("afk.placeholder.not_afk", "&a● ");
        return m;
    }

    @Override
    public void initialize() {
        this.service = new AfkService(this);

        this.api = new AfkAPI(this);
        APIRegistry.register(AfkAPI.class, this.api);

        getLifecycleManager().getCommandManager().registerFeatureCommand(new AfkCommand(this));
        getLifecycleManager().getListenerManager().registerListener(new ActivityListener(this));

        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                () -> {
                    try { service.tickCheck(); } catch (Throwable t) {
                        getLogger().warning("AFK tick error: " + t.getMessage());
                    }
                },
                BukkitTime.ticks(20L),
                BukkitTime.ticks(20L)
        );

        if (getPlugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AfkPlaceholder(this).register();
        }
    }

    @Override
    public void disable() {
        if (service != null) service.cleanupOnDisable();
        APIRegistry.unregister(AfkAPI.class);
    }
}
