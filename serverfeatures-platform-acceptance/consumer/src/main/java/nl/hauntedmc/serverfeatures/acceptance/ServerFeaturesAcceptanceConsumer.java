package nl.hauntedmc.serverfeatures.acceptance;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuNavigator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

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
                verifyBuiltinCommandBlocker();
                getLogger().info("SERVERFEATURES_ACCEPTANCE_PASS platform=paper");
            } catch (Exception exception) {
                getLogger().severe("SERVERFEATURES_ACCEPTANCE_FAIL platform=paper cause=" + exception);
            }
        });
    }

    private void verifyBuiltinCommandBlocker() throws Exception {
        boolean valid = Bukkit.getScheduler().callSyncMethod(this, () ->
                Bukkit.getCommandMap().getCommand("version") == null
                        && Bukkit.getCommandMap().getCommand("version-alias") == null
                        && Bukkit.getCommandMap().getCommand("version-alias-chain") == null
                        && Bukkit.getCommandMap().getCommand("stop") != null
                        && Bukkit.getCommandMap().getCommand("serverfeatures") != null
        ).get(10L, TimeUnit.SECONDS);
        if (!valid) {
            throw new IllegalStateException(
                    "BuiltinCommandBlocker hard-removal acceptance failed: expected /version and its server aliases "
                            + "removed, /stop allowlisted and /serverfeatures preserved."
            );
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
