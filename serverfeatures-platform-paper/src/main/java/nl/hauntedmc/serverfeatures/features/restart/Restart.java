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
import nl.hauntedmc.serverfeatures.features.restart.listener.RestartJoinGuard;
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
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);

        // Countdown presentation.
        config.put("title_fade_in", 20);
        config.put("title_stay", 100);
        config.put("title_fade_out", 20);
        config.put("broadcast.use_chat", true);
        config.put("broadcast.use_titles", true);
        config.put("announce.schedule", List.of(60, 30, 10, 5, 4, 3, 2, 1, 0));

        // Daily automatic restart.
        config.put("auto.enabled", true);
        config.put("auto.time", "05:00");
        config.put("auto.wait_after_now_seconds", 5);

        // One-off scheduled restart command.
        config.put("schedule.time_zone", "system");
        config.put("schedule.check_interval_seconds", 5);
        config.put("schedule.announce_hours_before", 5);

        // Controlled player drain. Joins close before the first player is removed.
        config.put("drain.player_interval_millis", 150);
        config.put("drain.poll_interval_millis", 100);
        config.put("drain.empty_grace_millis", 300);
        config.put("drain.max_wait_seconds", 20);

        // Optional proxy-side reconnect after this backend has fully restarted.
        config.put("autoreconnect.enabled", true);
        config.put("autoreconnect.stream", DEFAULT_RESTART_STREAM);
        config.put("autoreconnect.wait_after_ready_seconds", 5);
        config.put("autoreconnect.player_interval_millis", 250);
        config.put("autoreconnect.prepare_publish_timeout_millis", 3000);
        config.put("autoreconnect.prepare_settle_millis", 500);
        config.put("autoreconnect.session_ttl_seconds", 600);
        config.put("autoreconnect.ready_publish_attempts", 12);
        config.put("autoreconnect.ready_retry_seconds", 5);

        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("restart.in_progress", "&eEr is al een restartproces actief.");
        messages.add("restart.started", "&aRestartsequentie gestart.");
        messages.add(
                "restart.forced",
                "&f&l[RESTART] &cGeforceerde restart gestart; spelers worden veilig verplaatst."
        );
        messages.add(
                "restart.command.usage",
                "&eGebruik: /restart [force|schedule <datum|dag> <tijd>|cancel|status]"
        );

        messages.add("restart.countdown.title", "&cServer restart over &f{readable}");
        messages.add("restart.countdown.subtitle", "&7Bereid jezelf voor!");
        messages.add(
                "restart.countdown.chat",
                "&f&l[RESTART] &cDe server gaat restarten over &f{readable}&c."
        );
        messages.add("restart.countdown.now.title", "&cServer Restart");
        messages.add("restart.countdown.now.subtitle", "&7Tot straks!");
        messages.add(
                "restart.countdown.now.chat",
                "&f&l[RESTART] &cDe server wordt nu leeggemaakt en herstart."
        );
        messages.add(
                "restart.kick",
                "&cDe server wordt herstart. Je wordt automatisch teruggebracht zodra deze gereed is."
        );
        messages.add(
                "restart.join_blocked",
                "&cDeze server wordt momenteel herstart. Probeer het over een moment opnieuw."
        );

        messages.add(
                "restart.schedule.usage",
                "&eGebruik: /restart schedule <YYYY-MM-DD|dag> <HH:mm>"
        );
        messages.add(
                "restart.schedule.invalid_datetime",
                "&cOngeldige datum of tijd. Voorbeeld: /restart schedule vrijdag 05:00"
        );
        messages.add(
                "restart.schedule.set",
                "&aDe serverrestart is gepland voor &f{datetime}&a."
        );
        messages.add(
                "restart.schedule.already_active",
                "&eEr is al een geplande of actieve restart. Annuleer deze eerst."
        );
        messages.add(
                "restart.schedule.time_must_be_future",
                "&cDe geplande restart moet in de toekomst liggen."
        );
        messages.add(
                "restart.schedule.announce_chat",
                "&f&l[RESTART] &eEr staat een serverrestart gepland voor &f{datetime}&e."
        );

        messages.add("restart.cancel.none", "&eEr is geen restart om te annuleren.");
        messages.add("restart.cancel.scheduled", "&aDe geplande restart is geannuleerd.");
        messages.add("restart.cancel.countdown", "&aDe restart-countdown is geannuleerd.");
        messages.add("restart.cancel.final_delay", "&aDe restart is op tijd geannuleerd.");
        messages.add(
                "restart.cancel.preparing",
                "&aDe restartvoorbereiding is geannuleerd; nieuwe joins zijn weer toegestaan."
        );
        messages.add(
                "restart.cancel.too_late",
                "&cDe spelers worden al verplaatst; annuleren is niet meer veilig mogelijk."
        );

        messages.add("restart.status.none", "&7Er is geen restart actief of gepland.");
        messages.add(
                "restart.status.scheduled",
                "&eRestart gepland voor &f{datetime}&e."
        );
        messages.add(
                "restart.status.countdown",
                "&eRestart-countdown actief: nog &f{seconds} &eseconden."
        );
        messages.add(
                "restart.status.final_delay",
                "&eDe countdown is voltooid; de restartvoorbereiding begint direct."
        );
        messages.add(
                "restart.status.preparing",
                "&eRestart wordt voorbereid; joins zijn gesloten. Spelers online: &f{players}&e."
        );
        messages.add(
                "restart.status.draining",
                "&eSpelers worden veilig verplaatst. Nog online: &f{players}&e."
        );
        messages.add(
                "restart.status.shutting_down",
                "&cAlle spelers zijn verplaatst; de server sluit nu af."
        );
        return messages;
    }

    @Override
    public void initialize() {
        initializeAutoreconnect();
        this.service = new RestartService(this, lifecyclePublisher);

        RestartCommand restartCommand = new RestartCommand(this, service);
        getLifecycleManager().getListenerManager().registerListener(
                new RestartJoinGuard(this, service)
        );

        CommandOverride.unregisterVanillaRestart(getPlugin().getServer(), getLogger());
        CommandOverride.takeoverRestart(
                getPlugin().getServer(),
                getLogger(),
                restartCommand,
                getPlugin().getName()
        );

        if (getBoolean("auto.enabled", false)) {
            this.auto = new AutoRestartScheduler(
                    this,
                    service,
                    getString("auto.time", "04:00"),
                    service.getScheduleZone()
            );
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
            service.shutdown();
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

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getConfigHandler().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object value = getConfigHandler().get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
        }
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        Object value = getConfigHandler().get(key);
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
        }
        return defaultValue;
    }

    public int getPositiveInt(String key, int defaultValue) {
        int value = getInt(key, defaultValue);
        return value > 0 ? value : defaultValue;
    }

    public long getPositiveLong(String key, long defaultValue) {
        long value = getLong(key, defaultValue);
        return value > 0L ? value : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object value = getConfigHandler().get(key);
        return value == null ? defaultValue : String.valueOf(value);
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
