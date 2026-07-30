package nl.hauntedmc.serverfeatures.features.restart;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.restart.command.RestartCommand;
import nl.hauntedmc.serverfeatures.features.restart.internal.AutoRestartScheduler;
import nl.hauntedmc.serverfeatures.features.restart.internal.CommandOverride;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import nl.hauntedmc.serverfeatures.features.restart.listener.RestartServerLoadListener;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartLifecyclePublisher;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartMarkerStore;
import nl.hauntedmc.serverfeatures.features.restart.meta.Meta;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class Restart extends BukkitBaseFeature<Meta> {

    private static final String DEFAULT_RESTART_STREAM = "server.restart.lifecycle";

    private RestartService service;
    private AutoRestartScheduler auto;
    private RestartLifecyclePublisher lifecyclePublisher;

    public Restart(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap c = new ConfigMap();
        c.put("enabled", false);

        // Title timings (ticks)
        c.put("title_fade_in", 20);
        c.put("title_stay", 100);
        c.put("title_fade_out", 20);

        // Announce schedule (seconds remaining)
        c.put("announce.schedule", List.of(60, 30, 10, 0));

        // Auto restart
        c.put("auto.enabled", true);
        c.put("auto.time", "05:00"); // HH:mm (server timezone)
        c.put("auto.wait_after_now_seconds", 5); // seconds to wait after the "now" message

        // Optional proxy-side reconnect after this backend has fully restarted.
        c.put("autoreconnect.enabled", true);
        c.put("autoreconnect.stream", DEFAULT_RESTART_STREAM);
        c.put("autoreconnect.wait_after_ready_seconds", 5);
        c.put("autoreconnect.player_interval_millis", 250);
        c.put("autoreconnect.prepare_publish_timeout_millis", 3000);
        c.put("autoreconnect.prepare_settle_millis", 500);
        c.put("autoreconnect.session_ttl_seconds", 600);
        c.put("autoreconnect.ready_publish_attempts", 12);
        c.put("autoreconnect.ready_retry_seconds", 5);

        return c;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("restart.in_progress", "&eEr is al een restart proces gestart.");
        m.add("restart.started", "&aRestart sequentie gestart.");
        m.add("restart.forced", "&f&l[RESTART] &cRestart geforceerd. &7Opslaan en direct herstarten...");
        m.add("restart.countdown.title", "&cServer restart over &f{readable}");
        m.add("restart.countdown.subtitle", "&7Bereid jezelf voor!");
        m.add("restart.countdown.chat", "&f&l[RESTART] &cDe server gaat restarten over &f{readable}&c.");
        m.add("restart.countdown.now.title", "&cServer Restart");
        m.add("restart.countdown.now.subtitle", "&7Tot straks!");
        m.add("restart.countdown.now.chat", "&f&l[RESTART] &cDe server gaat nu restarten...");
        m.add("restart.kick", "&cDe server wordt herstart. Je kunt zo weer joinen.");
        return m;
    }

    @Override
    public void initialize() {
        initializeAutoreconnect();
        this.service = new RestartService(this, lifecyclePublisher);

        RestartCommand restartCmd = new RestartCommand(this, service);

        CommandOverride.unregisterVanillaRestart(getPlugin().getServer(), getLogger());

        // Aggressively take over restart bind from minecraft, bukkit, spigot and paper
        CommandOverride.takeoverRestart(
                getPlugin().getServer(),
                getLogger(),
                restartCmd,
                getPlugin().getName()
        );

        if (getBoolean("auto.enabled", false)) {
            String time = getString("auto.time", "04:00");
            this.auto = new AutoRestartScheduler(this, service, time);
            this.auto.scheduleNext();
        }
    }

    @Override
    public void disable() {
        if (auto != null) {
            auto.cancel();
            auto = null;
        }
        if (service != null) {
            service.cancelIfRunning();
            service = null;
        }
        if (lifecyclePublisher != null) {
            lifecyclePublisher.close();
            lifecyclePublisher = null;
        }
    }

    private void initializeAutoreconnect() {
        if (!getBoolean("autoreconnect.enabled", false)) {
            return;
        }

        Optional<MessagingDatabaseProvider> redisProvider;
        try {
            getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
            redisProvider = getLifecycleManager()
                    .getDataManager()
                    .registerRedisMessagingProvider("restart-autoreconnect-redis", "hauntedmc");
        } catch (RuntimeException exception) {
            getLogger().warning(
                    "Restart autoreconnect is disabled because Redis messaging initialization failed: "
                            + rootMessage(exception)
            );
            return;
        }
        if (redisProvider.isEmpty()) {
            getLogger().warning(
                    "Restart autoreconnect is disabled because Redis messaging is unavailable."
            );
            return;
        }

        DurableMessagingDataAccess messaging = redisProvider.get().getDurableDataAccess();
        String configuredStream = getString("autoreconnect.stream", DEFAULT_RESTART_STREAM);
        String stream = configuredStream == null || configuredStream.isBlank()
                ? DEFAULT_RESTART_STREAM
                : configuredStream.trim();
        String serverName = getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                "server"
        );
        Path markerPath = getPlugin().getDataFolder()
                .toPath()
                .resolve("restart")
                .resolve("autoreconnect.properties");
        this.lifecyclePublisher = new RestartLifecyclePublisher(
                this,
                messaging,
                new RestartMarkerStore(markerPath),
                stream,
                serverName
        );
        getLifecycleManager().getListenerManager().registerListener(
                new RestartServerLoadListener(lifecyclePublisher)
        );
        getLogger().info(
                "Restart autoreconnect lifecycle messaging enabled for backend '" + serverName + "'."
        );
    }

    /* small helpers */
    public boolean getBoolean(String key, boolean def) {
        Object v = getConfigHandler().get(key);
        return (v instanceof Boolean b) ? b : def;
    }

    public int getInt(String key, int def) {
        Object v = getConfigHandler().get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Throwable ignored) {
        }
        return def;
    }

    public long getLong(String key, long def) {
        Object v = getConfigHandler().get(key);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Throwable ignored) {
        }
        return def;
    }

    public int getPositiveInt(String key, int def) {
        int value = getInt(key, def);
        return value > 0 ? value : def;
    }

    public long getPositiveLong(String key, long def) {
        long value = getLong(key, def);
        return value > 0L ? value : def;
    }

    public String getString(String key, String def) {
        Object v = getConfigHandler().get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
