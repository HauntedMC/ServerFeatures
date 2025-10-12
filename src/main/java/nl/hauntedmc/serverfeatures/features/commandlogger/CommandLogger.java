package nl.hauntedmc.serverfeatures.features.commandlogger;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.serverfeatures.features.commandlogger.listener.CommandListener;
import nl.hauntedmc.serverfeatures.features.commandlogger.meta.Meta;
import nl.hauntedmc.serverfeatures.features.commandlogger.service.CommandLogService;

public class CommandLogger extends BukkitBaseFeature<Meta> {

    private CommandLogService commandLogService;
    private ORMContext ormContext;

    public CommandLogger(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        // Nothing specific here; server name comes from global setting "server_name"
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        // Database init and ORM registration
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");

        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "ormConnection",
                PlayerEntity.class,
                CommandExecutionEntity.class
        ).orElseThrow();

        this.commandLogService = new CommandLogService(this);

        // Listeners
        getLifecycleManager().getListenerManager().registerListener(new CommandListener(this));
    }

    @Override
    public void disable() {
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public CommandLogService getCommandLogService() {
        return commandLogService;
    }

}
