package nl.hauntedmc.serverfeatures.acceptance;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuNavigator;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PaperPlayerDataConverter;
import nl.hauntedmc.serverfeatures.shaded.nbtapi.NBT;
import nl.hauntedmc.serverfeatures.shaded.nbtapi.iface.ReadWriteNBT;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Verifies the bundled ServerFeatures artifact against the current shared platform APIs. */
public final class ServerFeaturesAcceptanceConsumer extends JavaPlugin {

    private static final int LEGACY_PLAYER_DATA_VERSION = 4440;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Plugin registry = Bukkit.getPluginManager().getPlugin("DataRegistry");
                if (!(registry instanceof DataRegistryApiProvider provider)) {
                    throw new IllegalStateException("DataRegistry does not expose its public API.");
                }
                awaitReady(provider);
                if (Bukkit.getPluginManager().getPlugin("ServerFeatures") == null) {
                    throw new IllegalStateException("ServerFeatures did not remain enabled.");
                }
                if (MenuNavigator.class.getName().isBlank()) {
                    throw new IllegalStateException("ServerFeatures public API is unavailable.");
                }
                verifyPlayerDataMigration();
                getLogger().info("SERVERFEATURES_ACCEPTANCE_PASS platform=paper");
            } catch (Exception exception) {
                getLogger().severe(
                        "SERVERFEATURES_ACCEPTANCE_FAIL platform=paper cause=" + exception
                );
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void verifyPlayerDataMigration() throws Exception {
        int runtimeDataVersion = Bukkit.getUnsafe().getDataVersion();
        if (runtimeDataVersion <= LEGACY_PLAYER_DATA_VERSION) {
            throw new IllegalStateException(
                    "Paper runtime DataVersion is not newer than the migration fixture"
            );
        }

        ReadWriteNBT legacy = NBT.createNBTObject();
        legacy.setInteger("DataVersion", LEGACY_PLAYER_DATA_VERSION);
        legacy.setString("serverfeatures_acceptance_marker", "preserve-me");
        legacy.getCompoundList("Inventory");
        legacy.getCompoundList("EnderItems");

        PaperPlayerDataConverter converter = new PaperPlayerDataConverter();
        converter.verifyAvailable();
        ReadWriteNBT converted = converter.convertToCurrent(
                legacy,
                LEGACY_PLAYER_DATA_VERSION,
                runtimeDataVersion
        );
        if (converted.getInteger("DataVersion") != runtimeDataVersion) {
            throw new IllegalStateException("Paper PLAYER fixer returned the wrong DataVersion");
        }
        if (!"preserve-me".equals(converted.getString("serverfeatures_acceptance_marker"))) {
            throw new IllegalStateException("Paper PLAYER fixer discarded unrelated playerdata");
        }
    }

    private static void awaitReady(DataRegistryApiProvider provider) throws InterruptedException {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < deadline) {
            try {
                if (provider.getDataRegistry().isReady()) {
                    return;
                }
            } catch (IllegalStateException ignored) {
                // DataRegistry is still completing its asynchronous platform initialization.
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("DataRegistry public API did not become ready.");
    }
}
