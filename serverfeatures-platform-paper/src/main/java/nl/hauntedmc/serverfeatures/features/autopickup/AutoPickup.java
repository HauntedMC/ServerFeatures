package nl.hauntedmc.serverfeatures.features.autopickup;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.PauseMode;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.autopickup.command.AutoPickupCommand;
import nl.hauntedmc.serverfeatures.features.autopickup.config.AutoPickupSettings;
import nl.hauntedmc.serverfeatures.features.autopickup.entity.PlayerAutoPickupSettingEntity;
import nl.hauntedmc.serverfeatures.features.autopickup.listener.AutoPickupBlockDropListener;
import nl.hauntedmc.serverfeatures.features.autopickup.listener.AutoPickupPlayerListener;
import nl.hauntedmc.serverfeatures.features.autopickup.meta.Meta;
import nl.hauntedmc.serverfeatures.features.autopickup.persistence.AutoPickupPreferenceRepository;
import nl.hauntedmc.serverfeatures.features.autopickup.persistence.AutoPickupPreferenceService;
import nl.hauntedmc.serverfeatures.features.autopickup.transfer.AutoPickupTransferCommitter;
import nl.hauntedmc.serverfeatures.features.autopickup.transfer.AutoPickupTransferPlanner;
import nl.hauntedmc.serverfeatures.features.autopickup.transfer.DirectDropOriginClassifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class AutoPickup extends BukkitBaseFeature<Meta> {

    public static final String USE_PERMISSION = "serverfeatures.feature.autopickup.use";

    private final Map<UUID, Long> diagnosticWarnings = new HashMap<>();
    private AutoPickupSettings settings;
    private ORMContext ormContext;
    private AutoPickupPreferenceService preferenceService;
    private AutoPickupTransferPlanner transferPlanner;
    private AutoPickupTransferCommitter transferCommitter;
    private DirectDropOriginClassifier originClassifier;

    public AutoPickup(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("default-enabled", false);
        defaults.put("drop-policy.scope", "STRICT_DIRECT");
        defaults.put("drop-policy.worlds.mode", "BLACKLIST");
        defaults.put("drop-policy.worlds.values", List.of());
        defaults.put("drop-policy.allowed-game-modes", List.of("SURVIVAL", "ADVENTURE"));
        defaults.put("notification.inventory-full.enabled", true);
        defaults.put("notification.inventory-full.notify-on-partial", true);
        defaults.put("notification.inventory-full.cooldown-millis", 3000L);
        defaults.put("notification.inventory-full.duration-seconds", 2);
        defaults.put("persistence.retry.attempts", 3);
        defaults.put("persistence.retry.initial-delay-millis", 250L);
        defaults.put("persistence.retry.maximum-delay-millis", 2000L);
        defaults.put("persistence.shutdown-drain-timeout-millis", 1000L);
        defaults.put("diagnostics.warning-cooldown-millis", 30000L);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("autopickup.usage", "&eGebruik: /autopickup [on|off|toggle|status]");
        messages.add("autopickup.enabled", "&aAutoPickup is ingeschakeld.");
        messages.add("autopickup.disabled", "&7AutoPickup is uitgeschakeld.");
        messages.add("autopickup.already_enabled", "&eAutoPickup was al ingeschakeld.");
        messages.add("autopickup.already_disabled", "&eAutoPickup was al uitgeschakeld.");
        messages.add("autopickup.status.enabled", "&aAutoPickup staat ingeschakeld.");
        messages.add("autopickup.status.disabled", "&7AutoPickup staat uitgeschakeld.");
        messages.add("autopickup.status.loading", "&eJe AutoPickup-instelling wordt nog geladen.");
        messages.add("autopickup.status.unsaved", "&eDe huidige instelling is nog niet bevestigd opgeslagen.");
        messages.add("autopickup.command_queued", "&eJe wijziging wordt toegepast zodra je instelling is geladen.");
        messages.add("autopickup.save_retry", "&eDe huidige AutoPickup-instelling wordt opnieuw opgeslagen.");
        messages.add(
                "autopickup.load_failed",
                "&cJe AutoPickup-instelling kon niet worden geladen. Gebruik /autopickup on of off om een nieuwe keuze op te slaan."
        );
        messages.add(
                "autopickup.save_failed",
                "&cAutoPickup is voor deze sessie aangepast, maar de instelling kon niet worden opgeslagen."
        );
        messages.add(
                "autopickup.session_disabled",
                "&cAutoPickup is voor deze sessie uitgeschakeld door een onverwachte inventarisfout. Je items zijn zo veilig mogelijk hersteld."
        );
        messages.add(
                "autopickup.inventory_full",
                "&cJe inventaris zit vol. &7{remaining_amount} item(s) in {remaining_stacks} stack(s) liggen op de grond."
        );
        return messages;
    }

    @Override
    public void initialize() {
        settings = AutoPickupSettings.load(getConfigHandler());

        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "autoPickupOrmConnection",
                DatabaseType.MYSQL,
                "player_data_rw"
        );
        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "autoPickupOrmConnection",
                PlayerAutoPickupSettingEntity.class
        ).orElseThrow(() -> new IllegalStateException(
                "AutoPickup requires the MYSQL/player_data_rw connection and could not create its ORM context."
        ));

        transferPlanner = new AutoPickupTransferPlanner();
        transferCommitter = new AutoPickupTransferCommitter();
        originClassifier = new DirectDropOriginClassifier();
        preferenceService = new AutoPickupPreferenceService(
                this,
                settings,
                new AutoPickupPreferenceRepository(ormContext)
        );

        getLifecycleManager().getListenerManager().registerListener(new AutoPickupPlayerListener(this));
        getLifecycleManager().getListenerManager().registerListener(new AutoPickupBlockDropListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new AutoPickupCommand(this));

        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            preferenceService.initialize(player);
        }
    }

    @Override
    public void disable() {
        if (preferenceService != null) {
            preferenceService.close();
            preferenceService = null;
        }
        diagnosticWarnings.clear();
        transferPlanner = null;
        transferCommitter = null;
        originClassifier = null;
        ormContext = null;
    }

    public AutoPickupSettings settings() {
        return settings;
    }

    public AutoPickupPreferenceService preferences() {
        return preferenceService;
    }

    public AutoPickupTransferPlanner transferPlanner() {
        return transferPlanner;
    }

    public AutoPickupTransferCommitter transferCommitter() {
        return transferCommitter;
    }

    public DirectDropOriginClassifier originClassifier() {
        return originClassifier;
    }

    public void notifyInventoryFull(Player player, int remainingAmount, int remainingStacks) {
        Component message = getLocalizationHandler().getMessage("autopickup.inventory_full")
                .forAudience(player)
                .with("remaining_amount", remainingAmount)
                .with("remaining_stacks", remainingStacks)
                .build();
        AutoPickupSettings.NotificationSettings notification = settings.notification();
        ActionBars.service().send(
                player,
                message,
                notification.durationSeconds(),
                PauseMode.PAUSE_CYCLE
        );
    }

    public void clearPlayerDiagnostics(UUID playerId) {
        diagnosticWarnings.remove(playerId);
    }

    public void reportTransferFailure(Player player, Throwable throwable) {
        long now = System.nanoTime();
        long previous = diagnosticWarnings.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous == Long.MIN_VALUE || now - previous >= settings.diagnosticWarningCooldownNanos()) {
            diagnosticWarnings.put(player.getUniqueId(), now);
            getLogger().log(
                    Level.WARNING,
                    "AutoPickup transfer failed for " + player.getUniqueId()
                            + " at " + player.getWorld().getName() + " "
                            + player.getLocation().getBlockX() + ","
                            + player.getLocation().getBlockY() + ","
                            + player.getLocation().getBlockZ(),
                    throwable
            );
        }
    }
}
