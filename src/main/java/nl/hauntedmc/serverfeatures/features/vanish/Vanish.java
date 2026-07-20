package nl.hauntedmc.serverfeatures.features.vanish;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.vanish.command.VanishCommand;
import nl.hauntedmc.serverfeatures.features.vanish.entities.PlayerVanishEntity;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishAPI;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishPlaceholder;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishRepository;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishService;
import nl.hauntedmc.serverfeatures.features.vanish.internal.messaging.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.vanish.listener.InteractionListener;
import nl.hauntedmc.serverfeatures.features.vanish.listener.TabListener;
import nl.hauntedmc.serverfeatures.features.vanish.listener.VisibilityListener;
import nl.hauntedmc.serverfeatures.features.vanish.meta.Meta;

import java.util.Optional;

public class Vanish extends BukkitBaseFeature<Meta> {

    private VanishService service;
    private VanishRepository repository;
    private ORMContext ormContext;

    // Redis messaging (optional)
    private EventBusHandler eventBusHandler;

    private VanishAPI api;

    public Vanish(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    public VanishService getService() {
        return service;
    }

    public VanishRepository getRepository() {
        return repository;
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("set_invisible_flag", true);
        cfg.put("disable_collisions", true);
        cfg.put("prevent_item_pickup", true);
        cfg.put("prevent_damage_and_interact", true);
        cfg.put("prevent_entity_targeting", true);
        cfg.put("filter_tab_completion", true);
        cfg.put("actionbar_interval_ticks", 40);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        m.add("vanish.enabled_self", "&7[&cVanish&7] &fJe zit nu in vanish mode.");
        m.add("vanish.disabled_self", "&7[&cVanish&7] &fJe zit niet meer in vanish mode.");
        m.add("vanish.usage", "&eGebruik: /vanish [on|off|<speler>]");
        m.add("vanish.already_state", "&cDeze speler zit al in deze modus.");
        m.add("vanish.not_online", "&cDie speler is niet online.");
        m.add("vanish.enabled_other", "&7[&cVanish&7] &e{target}&f is nu onzichtbaar!");
        m.add("vanish.disabled_other", "&7[&cVanish&7] &e{target}&f is nu weer zichtbaar!");
        m.add("vanish.target_enabled_by_other", "&7[&cVanish&7] &f{actor} heeft je in vanish mode gezet.");
        m.add("vanish.target_disabled_by_other", "&7[&cVanish&7] &f{actor} heeft je uit vanish mode gehaald.");
        m.add("vanish.staff_enabled", "&7[&cVanish&7] &f{target} zit nu in vanish mode.");
        m.add("vanish.staff_disabled", "&7[&cVanish&7] &f{target} zit niet meer in vanish mode.");
        m.add("vanish.staff_joined_vanished", "&7[&cVanish&7] &f{name} heeft de gamemode in vanish mode gejoined.");
        m.add("vanish.actionbar", "&eJe bent nu onzichtbaar voor anderen.");

        return m;
    }

    @Override
    public void initialize() {
        // --- Data layer (ORM) ---
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "ormConnection",
                PlayerVanishEntity.class
        ).orElseThrow();

        this.repository = new VanishRepository(this);

        // Service (runtime logic)
        this.service = new VanishService(this);

        this.api = new VanishAPI(this);
        getLifecycleManager().getApiManager().registerService(VanishAPI.class, this.api);

        // Commands
        getLifecycleManager().getCommandManager().registerFeatureCommand(new VanishCommand(this));

        // Listeners
        getLifecycleManager().getListenerManager().registerListener(new VisibilityListener(this));
        getLifecycleManager().getListenerManager().registerListener(new InteractionListener(this));
        getLifecycleManager().getListenerManager().registerListener(new TabListener(this));

        // Actionbar loop
        int interval = Math.max(5, (int) getConfigHandler().get("actionbar_interval_ticks"));
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                service.tickActionBars();
            } catch (Throwable t) {
                getLogger().warning("Actionbar tick error: " + t.getMessage());
            }
        }, BukkitTime.ticks(interval), BukkitTime.ticks(interval));

        // Register PlaceholderAPI expansion
        if (getPlugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VanishPlaceholder(this).register();
        }

        // --- Redis messaging (optional) ---
        try {
            Optional<MessagingDataAccess> redisBus = getLifecycleManager()
                    .getDataManager()
                    .registerDataAccess("redis", DatabaseType.REDIS_MESSAGING, "hauntedmc", MessagingDataAccess.class);

            if (redisBus.isPresent()) {
                String serverName = (String) getConfigHandler().getGlobalSetting("server_name");
                if (serverName == null || serverName.isEmpty()) {
                    getLogger().warning("Global setting 'server_name' is missing; vanish update messages will still publish but without server attribution.");
                }

                this.eventBusHandler = new EventBusHandler(this, redisBus.get(), serverName);
                getLogger().info("Redis messaging for Vanish initialized.");
            } else {
                getLogger().warning("Redis messaging connection 'redis' not available. Vanish updates will not be published to proxy.");
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to initialize Redis messaging for Vanish: " + t.getMessage());
        }
    }

    @Override
    public void disable() {
        // Restore player flags on shutdown
        if (service != null) {
            service.cleanupOnDisable();
        }
    }

    public VanishAPI getApi() {
        return api;
    }
}
