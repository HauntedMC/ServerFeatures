package nl.hauntedmc.serverfeatures.features.vanish;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.vanish.command.VanishCommand;
import nl.hauntedmc.serverfeatures.features.vanish.entities.PlayerVanishEntity;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishRepository;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishService;
import nl.hauntedmc.serverfeatures.features.vanish.listener.InteractionListener;
import nl.hauntedmc.serverfeatures.features.vanish.listener.TabListener;
import nl.hauntedmc.serverfeatures.features.vanish.listener.VisibilityListener;
import nl.hauntedmc.serverfeatures.features.vanish.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class Vanish extends BukkitBaseFeature<Meta> {

    private VanishService service;
    private VanishRepository repository;
    private ORMContext ormContext;
    private BukkitTask actionBarTask;

    public Vanish(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    public VanishService getService() { return service; }
    public VanishRepository getRepository() { return repository; }
    public ORMContext getOrmContext() { return ormContext; }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);
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

        // Commands
        m.add("vanish.usage", "&eGebruik: /vanish [on|off|<speler>]");
        m.add("vanish.enabled_self", "&aVanish ingeschakeld.");
        m.add("vanish.disabled_self", "&eVanish uitgeschakeld.");
        m.add("vanish.enabled_other", "&aVanish ingeschakeld voor &f{target}&a.");
        m.add("vanish.disabled_other", "&eVanish uitgeschakeld voor &f{target}&e.");
        m.add("vanish.already_state", "&cSpeler staat al in deze modus.");
        m.add("vanish.not_online", "&cDie speler is niet online.");

        // Staff notifications
        m.add("vanish.staff_enabled", "&7[Vanish] &f{target}&7 is nu &aaan&7 (door &f{actor}&7).");
        m.add("vanish.staff_disabled", "&7[Vanish] &f{target}&7 is nu &coff&7 (door &f{actor}&7).");
        m.add("vanish.staff_joined_vanished", "&7[Vanish] &f{name}&7 heeft de server betreden in &aaan&7.");

        // Actionbar
        m.add("vanish.actionbar", "&aVanish actief");

        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ormContext = getLifecycleManager().getDataManager().createORMContext("ormConnection",
                PlayerEntity.class,
                PlayerVanishEntity.class
        ).orElseThrow();

        this.repository = new VanishRepository(this);

        // Service (runtime logic)
        this.service = new VanishService(this);

        // Commands
        getLifecycleManager().getCommandManager().registerFeatureCommand(new VanishCommand(this));

        // Listeners
        getLifecycleManager().getListenerManager().registerListener(new VisibilityListener(this));
        getLifecycleManager().getListenerManager().registerListener(new InteractionListener(this));
        getLifecycleManager().getListenerManager().registerListener(new TabListener(this));

        // Actionbar loop
        int interval = Math.max(5, (int) getConfigHandler().getSetting("actionbar_interval_ticks"));
        this.actionBarTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            try { service.tickActionBars(); } catch (Throwable t) { getLogger().warning("Actionbar tick error: " + t.getMessage()); }
        }, interval, interval);
    }

    @Override
    public void disable() {
        // Cancel tasks
        if (actionBarTask != null) {
            try { actionBarTask.cancel(); } catch (Throwable ignored) {}
            actionBarTask = null;
        }
        // Restore player flags on shutdown
        if (service != null) {
            service.cleanupOnDisable();
        }
    }
}
