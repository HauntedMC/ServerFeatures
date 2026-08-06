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
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerIdentityEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
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
import java.util.List;
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
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("network_key", "hauntedmc");
        defaults.put("server_key", "$server");
        defaults.put("database.connection", "system_data_rw");
        defaults.put("messaging.enabled", true);
        defaults.put("messaging.connection", "hauntedmc");
        defaults.put("messaging.channel", "serverfeatures.economy.balance");
        defaults.put("cache.authoritative_refresh_interval", "10s");
        defaults.put("vault.enabled", true);
        defaults.put("vault.primary_currency", "money");
        defaults.put("vault.conflict_policy", "FAIL");

        defaults.put("currencies.money.enabled", true);
        defaults.put("currencies.money.scope.type", "SERVER");
        defaults.put("currencies.money.display.singular", "coin");
        defaults.put("currencies.money.display.plural", "coins");
        defaults.put("currencies.money.display.symbol", "$");
        defaults.put("currencies.money.display.format", "{symbol}{amount}");
        defaults.put("currencies.money.display.fractional_digits", 2);
        defaults.put("currencies.money.display.grouping", true);
        defaults.put("currencies.money.balances.starting", "0.00");
        defaults.put("currencies.money.balances.minimum", "0.00");
        defaults.put("currencies.money.balances.maximum", "999999999999.99");
        defaults.put("currencies.money.balances.allow_negative", false);
        defaults.put("currencies.money.balances.rounding", "HALF_UP");
        defaults.put("currencies.money.commands.root", "money");
        defaults.put("currencies.money.commands.aliases", List.of("balance", "bal"));
        defaults.put("currencies.money.commands.balance", true);
        defaults.put("currencies.money.commands.balance_others", true);
        defaults.put("currencies.money.commands.pay", true);
        defaults.put("currencies.money.commands.paytoggle", true);
        defaults.put("currencies.money.commands.history", true);
        defaults.put("currencies.money.commands.top", false);
        defaults.put("currencies.money.payments.default_enabled", true);
        defaults.put("currencies.money.payments.minimum", "0.01");
        defaults.put("currencies.money.payments.maximum", "1000000.00");
        defaults.put("currencies.money.payments.confirmation_threshold", "100000.00");
        defaults.put("currencies.money.payments.daily_send_limit", "0.00");
        defaults.put("currencies.money.payments.daily_receive_limit", "0.00");
        defaults.put("currencies.money.payments.cooldown", "1s");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("economy.player_only", "<red>Dit commando kan alleen door een speler worden gebruikt.</red>");
        messages.add("economy.error", "<red>De economieactie is mislukt: {reason}</red>");
        messages.add("economy.invalid_amount", "<red>Ongeldig bedrag: {reason}</red>");
        messages.add("economy.balance.self", "<gray>Je saldo:</gray> <gold>{balance}</gold>");
        messages.add("economy.balance.other", "<gray>Saldo van {player}:</gray> <gold>{balance}</gold>");
        messages.add("economy.pay.cooldown", "<yellow>Wacht nog {seconds} seconde(n) voor een nieuwe betaling.</yellow>");
        messages.add("economy.pay.confirm", "<yellow>Bevestig de betaling van {amount} aan {player} met <white>{command}</white>.</yellow>");
        messages.add("economy.pay.no_confirmation", "<yellow>Er staat geen geldige betaling klaar om te bevestigen.</yellow>");
        messages.add("economy.pay.failed", "<red>De betaling is mislukt: {reason}</red>");
        messages.add("economy.pay.sent", "<green>Je betaalde {amount} aan {player}.</green> <gray>Nieuw saldo: {balance}</gray>");
        messages.add("economy.pay.received", "<green>Je ontving {amount} van {player}.</green> <gray>Nieuw saldo: {balance}</gray>");
        messages.add("economy.paytoggle.enabled", "<green>Je accepteert betalingen van andere spelers.</green>");
        messages.add("economy.paytoggle.disabled", "<yellow>Je accepteert geen betalingen van andere spelers.</yellow>");
        messages.add("economy.history.header", "<gold><bold>Transactiegeschiedenis</bold></gold> <gray>pagina {page}</gray>");
        messages.add("economy.history.empty", "<gray>Geen transacties gevonden.</gray>");
        messages.add("economy.history.entry", "<gray>{type}</gray> <white>{amount}</white> <dark_gray>→ {balance} · {operation}</dark_gray>");
        messages.add("economy.top.header", "<gold><bold>Ranglijst</bold></gold> <gray>pagina {page}</gray>");
        messages.add("economy.top.entry", "<aqua>#{rank}</aqua> <white>{player}</white> <gray>· {balance}</gray>");
        messages.add("economy.admin.status", "<gold>Economy</gold> <gray>· server {server} · {currencies} currencies · Vault {vault} · messaging {messaging}</gray>");
        messages.add("economy.admin.currency", "<white>{currency}</white> <gray>· {scope} · {scope_key} · {command}</gray>");
        messages.add("economy.admin.balance", "<white>{player}</white> <gray>· {currency} · {scope} ·</gray> <gold>{balance}</gold>");
        messages.add("economy.admin.reason_required", "<red>Een reden is verplicht.</red>");
        messages.add("economy.admin.changed", "<green>Saldo van {player} ({currency}) aangepast naar {balance}.</green> <gray>Transactie {operation}</gray>");
        messages.add("economy.admin.payments", "<green>Betalingen voor {player} staan nu {state}.</green>");
        messages.add("economy.admin.frozen", "<yellow>Account {player}/{currency} is bevroren.</yellow>");
        messages.add("economy.admin.unfrozen", "<green>Account {player}/{currency} is vrijgegeven.</green>");
        messages.add("economy.admin.verify", "<gray>Status {health} · accounts {accounts} · transacties {transactions} · ongeldige saldi {invalid} · ongeldige regels {invalid_entries} · ongeldige transacties {invalid_transactions} · losse instellingen {orphan_settings} · losse regels {orphan_entries} · identiteitsfouten {identity_mismatches} · verkeerde accountregels {entry_account_mismatches} · accounts zonder journaal {accounts_without_entries} · lege transacties {empty_transactions} · saldo/journaalfouten {balance_journal_mismatches} · ketenfouten {continuity_errors}</gray>");
        return messages;
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
            placeholder.unregister();
            placeholder = null;
        }
        if (vault != null) {
            vault.close();
            vault = null;
        }
        vaultStatus = "disabled";
        if (messaging != null) {
            messaging.close();
            messaging = null;
        }
        if (service != null) {
            service.close();
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
        try {
            Class<?> implementation = Class.forName(
                    "nl.hauntedmc.serverfeatures.features.economy.vault.VaultProviderRegistration",
                    true,
                    getClass().getClassLoader()
            );
            EconomyVaultIntegration integration = implementation
                    .asSubclass(EconomyVaultIntegration.class)
                    .getConstructor(Economy.class)
                    .newInstance(this);
            integration.register();
            vault = integration;
            vaultStatus = integration.status();
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Could not initialize Vault economy integration", cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Could not initialize Vault economy integration", exception);
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
