package nl.hauntedmc.serverfeatures.acceptance;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuNavigator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/** Verifies the bundled ServerFeatures artifact against the current shared platform APIs. */
public final class ServerFeaturesAcceptanceConsumer extends JavaPlugin {
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

                // Run after the server has entered its ticking lifecycle. This also gives ServerLoadEvent command
                // refreshes one full tick to settle before validating the final command registry.
                Bukkit.getScheduler().runTaskLater(this, this::runStableVerification, 20L);
            } catch (Exception exception) {
                getLogger().severe("SERVERFEATURES_ACCEPTANCE_FAIL platform=paper cause=" + exception);
            }
        });
    }

    private void runStableVerification() {
        try {
            Map<String, Command> knownCommands = Bukkit.getCommandMap().getKnownCommands();
            boolean versionRemoved = !knownCommands.containsKey("version");
            boolean directAliasRemoved = !knownCommands.containsKey("version-alias");
            boolean chainedAliasRemoved = !knownCommands.containsKey("version-alias-chain");
            boolean seedRemoved = !knownCommands.containsKey("seed") && !knownCommands.containsKey("minecraft:seed");
            boolean stopPreserved = knownCommands.containsKey("stop") || knownCommands.containsKey("minecraft:stop");
            boolean serverFeaturesPreserved = knownCommands.containsKey("serverfeatures");

            if (!versionRemoved
                    || !directAliasRemoved
                    || !chainedAliasRemoved
                    || !seedRemoved
                    || !stopPreserved
                    || !serverFeaturesPreserved) {
                throw new IllegalStateException(
                        "BuiltinCommandBlocker hard-removal acceptance failed: versionRemoved=" + versionRemoved
                                + ", directAliasRemoved=" + directAliasRemoved
                                + ", chainedAliasRemoved=" + chainedAliasRemoved
                                + ", seedRemoved=" + seedRemoved
                                + ", stopPreserved=" + stopPreserved
                                + ", serverFeaturesPreserved=" + serverFeaturesPreserved
                );
            }
            getLogger().info("SERVERFEATURES_ACCEPTANCE_PASS platform=paper");
        } catch (Throwable throwable) {
            getLogger().severe("SERVERFEATURES_ACCEPTANCE_FAIL platform=paper cause=" + throwable);
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
