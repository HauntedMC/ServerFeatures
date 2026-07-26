package nl.hauntedmc.serverfeatures.features.sanctions;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
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

    public Sanctions(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
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
                        SanctionEntity.class)
                .orElseThrow();

        this.service = new SanctionsDataService(this);
        this.muteRegistry = new MuteRegistry(service);

        MuteListener muteListener = new MuteListener(this, muteRegistry, service);
        getLifecycleManager().getListenerManager().registerListener(muteListener);

        // Warm up through the same DataRegistry readiness gate as ordinary joins.
        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            muteListener.restoreMuteState(player);
        }

        // Global periodic refresh of active mutes (once per configured interval)
        int seconds = ((Number) getConfigHandler().get("muteRefreshSeconds")).intValue();
        seconds = Math.max(10, seconds); // guardrails
        getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(
                () -> {
                    try {
                        muteRegistry.refreshAll();
                    } catch (Throwable throwable) {
                        getLogger().warning(
                                "Could not refresh online mute state: " + throwable.getMessage()
                        );
                    }
                },
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
