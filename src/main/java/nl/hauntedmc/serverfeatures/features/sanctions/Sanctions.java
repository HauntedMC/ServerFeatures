package nl.hauntedmc.serverfeatures.features.sanctions;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.sanctions.listener.MuteListener;
import nl.hauntedmc.serverfeatures.features.sanctions.meta.Meta;
import nl.hauntedmc.serverfeatures.features.sanctions.service.SanctionsDataService;
import nl.hauntedmc.serverfeatures.features.sanctions.state.MuteRegistry;
import org.bukkit.entity.Player;

public class Sanctions extends BukkitBaseFeature<Meta> {

    private ORMContext orm;
    private SanctionsDataService service;
    private MuteRegistry muteRegistry;

    public Sanctions(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", true);
        defaults.put("muteRefreshSeconds", 60); // how often to re-check DB for muted players
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("sanctions.chat_blocked.temp",
                "&8&l[&c&lSanctions&8&l]&r &cJe bent gemute.&7 Resterende tijd: &f{remaining}&7. Reden: &f{reason}");
        m.add("sanctions.chat_blocked.perm",
                "&8&l[&c&lSanctions&8&l]&r &cJe bent &lpermanent &r&cgemute.&7 Reden: &f{reason}");
        return m;
    }

    @Override
    public void initialize() {
        // DB & ORM setup
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "orm", DatabaseType.MYSQL, "player_data_rw");
        orm = getLifecycleManager().getDataManager()
                .createORMContext("orm",
                        PlayerEntity.class,
                        SanctionEntity.class)
                .orElseThrow();

        this.service = new SanctionsDataService(this);
        this.muteRegistry = new MuteRegistry(service);

        getLifecycleManager().getListenerManager().registerListener(new MuteListener(this, muteRegistry, service));

        // Warm-up: check all currently online players for active mutes (run async)
        getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            int checked = 0;
            for (Player p : getPlugin().getServer().getOnlinePlayers()) {
                try {
                    // Populate/refresh this player's mute status immediately
                    muteRegistry.trackIfMuted(p.getUniqueId());
                    checked++;
                } catch (Throwable t) {
                    getLogger().warning("[Sanctions] Warm-up failed for "+p.getName()+": " + t.getMessage());
                }
            }
            if (checked > 0) {
                getLogger().info("[Sanctions] Warmed up mute state for "+checked+" online player(s).");
            }
        });

        // Global periodic refresh of active mutes (once per configured interval)
        int seconds = ((Number) getConfigHandler().getSetting("muteRefreshSeconds")).intValue();
        seconds = Math.max(10, seconds); // guardrails
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                () -> muteRegistry.refreshAll(),
                BukkitTime.seconds(0),
                BukkitTime.ticks(seconds * 20L)
        );
    }

    @Override
    public void disable() {
        if (muteRegistry != null) muteRegistry.clear();
    }

    public ORMContext getOrm() {
        return orm;
    }

    public SanctionsDataService getService() {
        return service;
    }

}
