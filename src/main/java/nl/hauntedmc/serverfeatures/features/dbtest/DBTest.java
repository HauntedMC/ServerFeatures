package nl.hauntedmc.serverfeatures.features.dbtest;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.platform.bukkit.BukkitDataProvider;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.dbtest.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.Bukkit;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class DBTest extends BaseFeature<Meta> {

    private ORMContext ormContext;
    private DataProviderAPI dataProviderAPI;

    public DBTest(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    /**
     * If you have any default messages you want to provide, you can add them here.
     * For example:
     */
    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void initialize() {
        dataProviderAPI = BukkitDataProvider.getDataProviderAPI();
        dataProviderAPI.authenticate(getFeatureName(), "c5c052c7-b1a3-4c58-8b04-78496b2d4bd8");

        DatabaseProvider provider = dataProviderAPI.registerDatabase(getFeatureName(), DatabaseType.MYSQL, "test_conn");
        if (provider == null || !provider.isConnected()) {
            getPlugin().getLogger().severe("Database Provider is not connected.");
            return;
        }

        RelationalDatabaseProvider relationalProvider = (RelationalDatabaseProvider) provider;
        DataSource dataSource = relationalProvider.getDataSource();
        ormContext = new ORMContext(getFeatureName(), dataSource,
                PlayerEntity.class);

        // Instantiate the PlayerRepository.
        PlayerRepository playerRepository = BukkitDataRegistry.getPlayerRepository();
        Bukkit.broadcast(Component.text(playerRepository.findAll().stream().map(PlayerEntity::getUsername).toList().toString()));
    }

    @Override
    public void disable() {
        ormContext.shutdown();
        dataProviderAPI.unregisterAllDatabases(getFeatureName());
    }


}
