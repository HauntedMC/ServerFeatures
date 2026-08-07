package nl.hauntedmc.serverfeatures.features.economy;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.economy.command.CurrencyCommand;
import nl.hauntedmc.serverfeatures.features.economy.command.EconomyAdminCommand;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomyDefaults;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerIdentityEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyWorkflowEntity;
import nl.hauntedmc.serverfeatures.features.economy.listener.EconomyPlayerListener;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyMessaging;
import nl.hauntedmc.serverfeatures.features.economy.meta.Meta;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;
import nl.hauntedmc.serverfeatures.features.economy.placeholder.EconomyPlaceholder;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import nl.hauntedmc.serverfeatures.features.economy.vault.EconomyVaultIntegration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Durable multi-currency economy with server, group and network-global scopes. */
public final class Economy extends BukkitBaseFeature<Meta> {
    private static final String ORM_CONNECTION = "economyOrmConnection";
    private static final String MESSAGING_CONNECTION = "economyMessagingConnection";

    private EconomySettings settings;
    private EconomyService service;
    private EconomyMessaging messaging;
    private EconomyVaultIntegration vault;
    private String vaultStatus = "disabled";
    private EconomyPlaceholder placeholder;

    public Economy(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        return EconomyDefaults.config();
    }

    @Override
    public MessageMap getDefaultMessages() {
        return EconomyDefaults.messages();
    }

    @Override
    public void initialize() {
        String serverName = getConfigHandler().getGlobalSetting("server_name", String.class, "server");
        settings = EconomySettings.load(getConfigHandler(), serverName);

        var dataManager = getLifecycleManager().getDataManager();
        dataManager.initDataProvider(getFeatureName());
        dataManager.registerConnection(ORM_CONNECTION, DatabaseType.MYSQL, settings.databaseConnection());
        ORMContext orm = dataManager.createORMContext(
                ORM_CONNECTION,
                EconomyCurrencyFamilyEntity.class,
                EconomyCurrencyDefinitionEntity.class,
                EconomyPlayerIdentityEntity.class,
                EconomyBalanceEntity.class,
                EconomyPlayerSettingsEntity.class,
                EconomyTransactionEntity.class,
                EconomyTransactionEntryEntity.class,
                EconomyWorkflowEntity.class,
                EconomyDailyUsageEntity.class
        ).orElseThrow(() -> new IllegalStateException(
                "Economy requires MYSQL/" + settings.databaseConnection() + " and could not create its ORM context."
        ));

        EconomyRepository repository = new EconomyRepository(orm);
        repository.validateDefinitions(settings);
        service = new EconomyService(this, settings, repository);
        getLifecycleManager().getApiManager().registerService(EconomyApi.class, service);

        if (settings.messaging().enabled()) {
            dataManager.registerRedisMessagingDataAccess(MESSAGING_CONNECTION, settings.messaging().connection())
                    .ifPresentOrElse(access -> {
                        messaging = new EconomyMessaging(this, service, access, settings.messaging().channel());
                        messaging.start();
                        service.setMessaging(messaging);
                    }, () -> getLogger().warning(
                            "Economy messaging is unavailable; database correctness remains active."
                    ));
        }

        for (EconomySettings.Currency currency : settings.currencies().values()) {
            getLifecycleManager().getCommandManager().registerBrigadierCommand(new CurrencyCommand(this, currency));
        }
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new EconomyAdminCommand(this));
        getLifecycleManager().getListenerManager().registerListener(new EconomyPlayerListener(this));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            EconomyPlaceholder candidate = new EconomyPlaceholder(this);
            if (candidate.register()) {
                placeholder = candidate;
            }
        }

        initializeVault();
        service.start();
    }

    @Override
    public void disable() {
        if (placeholder != null) {
            closeSafely("PlaceholderAPI integration", placeholder::unregister);
            placeholder = null;
        }
        if (vault != null) {
            closeSafely("Vault integration", vault::close);
            vault = null;
        }
        vaultStatus = "disabled";
        if (messaging != null) {
            closeSafely("Economy messaging", messaging::close);
            messaging = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        settings = null;
    }

    public EconomySettings settings() {
        return settings;
    }

    public EconomyService service() {
        return service;
    }

    public String vaultStatus() {
        EconomyVaultIntegration current = vault;
        return current == null ? vaultStatus : current.status();
    }

    private void initializeVault() {
        if (!settings.vault().enabled()) {
            vaultStatus = "disabled";
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            vaultStatus = "vault-missing";
            getLogger().warning("Vault integration is enabled, but Vault is not installed.");
            return;
        }
        EconomyVaultIntegration candidate = null;
        try {
            Class<?> implementation = Class.forName(
                    "nl.hauntedmc.serverfeatures.features.economy.vault.VaultProviderRegistration",
                    true,
                    getClass().getClassLoader()
            );
            candidate = implementation
                    .asSubclass(EconomyVaultIntegration.class)
                    .getConstructor(Economy.class)
                    .newInstance(this);
            candidate.register();
            vault = candidate;
            vaultStatus = candidate.status();
        } catch (InvocationTargetException exception) {
            closeFailedVault(candidate, exception);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Could not initialize Vault economy integration", cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            closeFailedVault(candidate, exception);
            throw new IllegalStateException("Could not initialize Vault economy integration", exception);
        } catch (RuntimeException exception) {
            closeFailedVault(candidate, exception);
            throw exception;
        }
    }

    /** Cleans up a partially registered optional hook while preserving the original failure. */
    private void closeFailedVault(EconomyVaultIntegration candidate, Throwable failure) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.close();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /** Continues feature shutdown when an optional integration cannot clean itself up. */
    private void closeSafely(String component, Runnable closeAction) {
        try {
            closeAction.run();
        } catch (RuntimeException exception) {
            getLogger().warning("Could not close " + component + ": " + exception.getMessage());
        }
    }

    public String messagingStatus() {
        return messaging == null ? "disabled" : "active";
    }

    public void send(CommandSender audience, String key) {
        send(audience, key, Map.of());
    }

    public void send(CommandSender audience, String key, Map<String, String> values) {
        audience.sendMessage(component(audience, key, values));
    }

    public Component component(CommandSender audience, String key, Map<String, String> values) {
        var placeholders = MessagePlaceholders.builder();
        values.forEach(placeholders::addString);
        return getLocalizationHandler().getMessage(key)
                .withPlaceholders(placeholders.build())
                .forAudience(audience)
                .build();
    }
}
