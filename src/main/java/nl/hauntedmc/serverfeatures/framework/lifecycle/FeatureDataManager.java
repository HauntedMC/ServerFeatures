package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DataAccess;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataprovider.logging.adapters.JulLoggerAdapter;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class FeatureDataManager {
    private static final String ORM_SCHEMA_MODE_CONFIG_KEY = "dataprovider_orm_schema_mode";
    private static final String DEFAULT_ORM_SCHEMA_MODE = "validate";

    private final ServerFeatures plugin;
    private final Supplier<DataProviderAPI> dataProviderApiSupplier;
    private final LoggerAdapter ormLogger;
    private final ConcurrentHashMap<String, ConnectionRegistration> connectionsByIdentifier;

    private ORMContext ormContext;
    private String featureName;
    private boolean initialized;


    /**
     * Constructs a new FeatureDataManager.
     *
     * @param plugin your main plugin class (for logging, etc.)
     */
    public FeatureDataManager(ServerFeatures plugin) {
        this(plugin, () -> resolveApiSafely(plugin));
    }

    FeatureDataManager(ServerFeatures plugin, DataProviderAPI dataProviderAPI) {
        this(plugin, () -> dataProviderAPI);
    }

    private FeatureDataManager(ServerFeatures plugin, Supplier<DataProviderAPI> dataProviderApiSupplier) {
        this.plugin = plugin;
        this.dataProviderApiSupplier = dataProviderApiSupplier;
        this.ormLogger = new JulLoggerAdapter(plugin.getLogger());
        this.connectionsByIdentifier = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the DataProvider usage scope for the given feature.
     *
     * @param featureName the name of the feature
     */
    public void initDataProvider(String featureName) {
        this.featureName = featureName;
        this.initialized = false;

        if (featureName == null || featureName.isBlank()) {
            plugin.getLogger().severe("Feature name cannot be null or blank.");
            return;
        }

        if (getDataProviderApi().isEmpty()) {
            plugin.getLogger().severe("DataProviderAPI is not available for feature '" + featureName + "'.");
            return;
        }

        initialized = true;
        plugin.getLogger().info(
                "DataProvider initialized for feature '" + featureName
                        + "'. Caller identity is resolved automatically by DataProvider."
        );
    }

    /**
     * Registers a connection (DatabaseProvider) for the given identifier, using the specified
     * database type and connection name.
     *
     * @param identifier     a label for the provider
     * @param databaseType   the type of database (e.g. MYSQL, MONGODB, etc.)
     * @param connectionName the name or key used to differentiate the database config
     * @return an Optional containing the DatabaseProvider if registration was successful; empty otherwise
     */
    public Optional<DatabaseProvider> registerConnection(String identifier, DatabaseType databaseType, String connectionName) {
        if (!isReady()) {
            return Optional.empty();
        }

        ConnectionRegistration existing = connectionsByIdentifier.get(identifier);
        if (existing != null) {
            if (existing.databaseType == databaseType
                    && existing.connectionName.equals(connectionName)
                    && isProviderConnected(existing.provider)) {
                return Optional.of(existing.provider);
            }
            releaseConnection(existing, identifier);
            connectionsByIdentifier.remove(identifier, existing);
        }

        Optional<DatabaseProvider> registered;
        try {
            Optional<DataProviderAPI> dataProviderApi = getDataProviderApi();
            if (dataProviderApi.isEmpty()) {
                plugin.getLogger().severe("DataProviderAPI is not available for feature '" + featureName + "'.");
                return Optional.empty();
            }
            registered = dataProviderApi.get().registerDatabaseOptional(databaseType, connectionName);
        } catch (Exception ex) {
            plugin.getLogger().severe(
                    "Failed to register database '" + identifier + "' (type=" + databaseType
                            + ", connection='" + connectionName + "') for feature '" + featureName + "'."
            );
            return Optional.empty();
        }

        if (registered.isEmpty() || !isProviderConnected(registered.get())) {
            plugin.getLogger().severe(
                    "Database provider '" + identifier + "' is null or not connected (type=" + databaseType
                            + ", connection='" + connectionName + "')."
            );
            return Optional.empty();
        }

        DatabaseProvider provider = registered.get();
        ConnectionRegistration newRegistration = new ConnectionRegistration(databaseType, connectionName, provider);
        ConnectionRegistration replaced = connectionsByIdentifier.put(identifier, newRegistration);
        if (replaced != null && replaced != newRegistration) {
            releaseConnection(replaced, identifier);
        }

        plugin.getLogger().info("Successfully registered connection '" + identifier + "' of type " + databaseType);
        return Optional.of(provider);
    }

    /**
     * Retrieves the DatabaseProvider associated with the given identifier.
     *
     * @param identifier the key used to register the provider
     * @return an Optional containing the DatabaseProvider, or empty if none is found
     */
    public Optional<DatabaseProvider> getDataProvider(String identifier) {
        ConnectionRegistration registration = connectionsByIdentifier.get(identifier);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(registration.provider);
    }

    public <T extends DataAccess> Optional<T> registerDataAccess(
            String identifier,
            DatabaseType databaseType,
            String connectionName,
            Class<T> expectedDataAccessType
    ) {
        return registerConnection(identifier, databaseType, connectionName)
                .flatMap(provider -> {
                    Optional<T> dataAccess = provider.getDataAccessOptional(expectedDataAccessType);
                    if (dataAccess.isEmpty()) {
                        plugin.getLogger().severe(
                                "Connection '" + identifier + "' is not compatible with expected data access type "
                                        + expectedDataAccessType.getSimpleName() + "."
                        );
                    }
                    return dataAccess;
                });
    }

    public <T extends DataAccess> Optional<T> getDataAccess(String identifier, Class<T> expectedDataAccessType) {
        return getDataProvider(identifier).flatMap(provider -> provider.getDataAccessOptional(expectedDataAccessType));
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
        Optional<DatabaseProvider> providerOptional = getDataProvider(identifier);
        if (providerOptional.isEmpty()) {
            plugin.getLogger().severe("Could not find database provider for identifier: " + identifier);
            return Optional.empty();
        }
        DatabaseProvider provider = providerOptional.get();

        Optional<DataSource> dataSource = provider.getDataSourceOptional();
        if (dataSource.isEmpty()) {
            plugin.getLogger().severe(
                    "Database '" + identifier + "' does not expose a DataSource. ORMContext requires a relational provider."
            );
            return Optional.empty();
        }

        try {
            String ownerName = (featureName == null || featureName.isBlank())
                    ? plugin.getClass().getSimpleName()
                    : featureName;
            this.ormContext = newOrmContext(ownerName, dataSource.get(), entityClasses);
            plugin.getLogger().info("Created ORMContext for identifier '" + identifier + "'");
            return Optional.of(ormContext);
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to create ORMContext for identifier '" + identifier + "'.");
            return Optional.empty();
        }
    }


    /**
     * Retrieves the single ORMContext that this manager holds, if any.
     *
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

        if (getDataProviderApi().isPresent()) {
            for (var entry : connectionsByIdentifier.entrySet()) {
                releaseConnection(entry.getValue(), entry.getKey());
            }
        }

        connectionsByIdentifier.clear();
        ormContext = null;
        initialized = false;
    }

    /**
     * @return the current count of active connections
     */
    public int getActiveConnCount() {
        return connectionsByIdentifier.size();
    }

    ORMContext newOrmContext(String ownerName, DataSource dataSource, Class<?>... entityClasses) {
        return new ORMContext(ownerName, dataSource, ormLogger, resolveOrmSchemaMode(), entityClasses);
    }

    private String resolveOrmSchemaMode() {
        String configured = plugin.getConfigHandler().getGlobalSetting(
                ORM_SCHEMA_MODE_CONFIG_KEY,
                String.class,
                DEFAULT_ORM_SCHEMA_MODE
        );
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return DEFAULT_ORM_SCHEMA_MODE;
    }

    private static DataProviderAPI resolveApiSafely(ServerFeatures plugin) {
        try {
            RegisteredServiceProvider<DataProviderAPI> registration =
                    Bukkit.getServicesManager().getRegistration(DataProviderAPI.class);
            if (registration == null) {
                return null;
            }
            return registration.getProvider();
        } catch (RuntimeException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("DataProviderAPI unavailable: " + ex.getMessage());
            }
            return null;
        }
    }

    private boolean isReady() {
        if (featureName == null) {
            plugin.getLogger().severe("Feature name is not set. Did you call initDataProvider()?");
            return false;
        }
        if (!initialized) {
            plugin.getLogger().severe("DataProvider is not initialized for feature '" + featureName + "'.");
            return false;
        }
        return true;
    }

    private boolean isProviderConnected(DatabaseProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            return provider.isConnected();
        } catch (Exception ex) {
            return false;
        }
    }

    private void releaseConnection(ConnectionRegistration registration, String identifier) {
        if (registration == null) {
            return;
        }
        try {
            Optional<DataProviderAPI> dataProviderApi = getDataProviderApi();
            if (dataProviderApi.isEmpty()) {
                return;
            }
            dataProviderApi.get().unregisterDatabase(registration.databaseType, registration.connectionName);
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Failed to unregister connection '" + identifier + "' (type=" + registration.databaseType
                            + ", connection='" + registration.connectionName + "')."
            );
        }
    }

    private Optional<DataProviderAPI> getDataProviderApi() {
        try {
            return Optional.ofNullable(dataProviderApiSupplier.get());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("DataProviderAPI unavailable: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private record ConnectionRegistration(
            DatabaseType databaseType,
            String connectionName,
            DatabaseProvider provider
    ) {
    }
}
