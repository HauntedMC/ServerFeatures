package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.platform.bukkit.BukkitDataProvider;
import nl.hauntedmc.serverfeatures.ServerFeatures;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureDataManager {

    private final ServerFeatures plugin;
    private final DataProviderAPI dataProviderAPI;
    private ORMContext ormContext;
    private String featureName;
    private final ConcurrentHashMap<String, DatabaseProvider> databaseProviders;


    /**
     * Constructs a new FeatureDataManager.
     * @param plugin your main plugin class (for logging, etc.)
     */
    public FeatureDataManager(ServerFeatures plugin) {
        dataProviderAPI = BukkitDataProvider.getDataProviderAPI();
        databaseProviders = new ConcurrentHashMap<>();
        this.plugin = plugin;
    }

    /**
     * Authenticates your feature with the DataProviderAPI using the provided token.
     *
     * @param featureName the name of the feature
     */
    public void initDataProvider(String featureName) {
        this.featureName = featureName;
        dataProviderAPI.authenticate(featureName, "7ebbeae6-b52e-484a-ae61-8a215f8efc2b");
        plugin.getLogger().info("DataProvider authenticated feature '" + featureName + "' with token.");
    }

    /**
     * Registers a connection (DatabaseProvider) for the given identifier, using the specified
     * database type and connection name.
     *
     * @param identifier       a label for the provider
     * @param databaseType     the type of database (e.g. MYSQL, MONGODB, etc.)
     * @param connectionName   the name or key used to differentiate the database config
     * @return an Optional containing the DatabaseProvider if registration was successful; empty otherwise
     */
    public Optional<DatabaseProvider> registerConnection(String identifier, DatabaseType databaseType, String connectionName) {

        if (featureName == null) {
            plugin.getLogger().severe("Feature name is not set. Did you call initDataProvider()?");
            return Optional.empty();
        }

        DatabaseProvider provider =  dataProviderAPI.registerDatabase(featureName, databaseType, connectionName);
        if (provider == null || !provider.isConnected()) {
            plugin.getLogger().severe("Database Provider is not connected.");
            return Optional.empty();
        }
        databaseProviders.put(identifier, provider);
        plugin.getLogger().info("Successfully registered connection '" + identifier + "' of type " + databaseType);
        return Optional.of(provider);
    }

    /**
     * Retrieves the DatabaseProvider associated with the given identifier.
     * @param identifier the key used to register the provider
     * @return an Optional containing the DatabaseProvider, or empty if none is found
     */
    public Optional<DatabaseProvider> getDataProvider(String identifier) {
        return Optional.ofNullable(databaseProviders.get(identifier));
    }


    /**
     * Creates an ORM context for the specified database identifier and entity classes.
     * This overwrites any previously-created single ORM context in this manager.
     *
     * @param identifier    the database connection identifier
     * @param entityClasses the entity classes you want to manage with ORM
     * @return an Optional containing the newly created ORMContext, or empty if creation fails
     */
    public Optional<ORMContext> createORMContext(String identifier, Class<?>... entityClasses) {
        DatabaseProvider provider = databaseProviders.get(identifier);
        if (provider == null) {
            plugin.getLogger().severe("Could not find database provider for identifier: " + identifier);
            return Optional.empty();
        }

        // Create and store the ORMContext
        this.ormContext = new ORMContext(featureName, provider.getDataSource(), entityClasses);
        plugin.getLogger().info("Created ORMContext for identifier '" + identifier + "'");
        return Optional.of(ormContext);
    }


    /**
     * Retrieves the single ORMContext that this manager holds, if any.
     * @return an Optional of the current ORMContext
     */
    public Optional<ORMContext> getORMContext() {
        return Optional.ofNullable(ormContext);
    }


    /**
     * Closes the current ORMContext (if any) and unregisters all databases associated with
     * this feature name.
     */
    public void closeAllConnections() {
        if (ormContext != null) {
            ormContext.shutdown();
            plugin.getLogger().info("ORMContext has been shut down.");
        }

        if (!databaseProviders.isEmpty() && featureName != null) {
            dataProviderAPI.unregisterAllDatabases(featureName);
            plugin.getLogger().info("Unregistered all databases for feature '" + featureName + "'.");
        }

        databaseProviders.clear();
        ormContext = null;
    }

    /**
     * @return the current count of active connections
     */
    public int getActiveConnCount() {
        return databaseProviders.size();
    }
}
