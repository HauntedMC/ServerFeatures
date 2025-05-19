package nl.hauntedmc.serverfeatures.features.dbtest;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.dbtest.meta.Meta;
import org.bukkit.Bukkit;

public class DBTest extends BukkitBaseFeature<Meta> {


    public DBTest(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
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
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        getLifecycleManager().getDataManager().createORMContext("ormConnection", PlayerEntity.class);

        testFunction();
    }

    private void testFunction() {
        PlayerRepository playerRepository = BukkitDataRegistry.getPlayerRepository();
        Bukkit.broadcast(Component.text(playerRepository.findAll().stream().map(PlayerEntity::getUsername).toList().toString()));
    }

    @Override
    public void disable() {
    }


}
