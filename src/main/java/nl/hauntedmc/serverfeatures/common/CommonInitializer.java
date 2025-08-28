package nl.hauntedmc.serverfeatures.common;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs common pre-feature initialization steps that must happen as soon as the plugin enables,
 * before any feature modules are loaded. Keep this class focused on cross-cutting bootstrap logic.
 */
public final class CommonInitializer {

    private final Logger logger;

    public CommonInitializer(ServerFeatures plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * Entry point for pre-feature initialization. Called from {@link ServerFeatures#onEnable()} before
     * feature initialization kicks in.
     */
    public void runPreFeatureInitialization() {
        initializeScoreboardsForCurrentlyOnlinePlayers();
    }

    /**
     * Ensures any players already online at plugin enable receive a fresh personal scoreboard,
     * equivalent to the logic executed on {@code PlayerJoinEvent}.
     *
     * This is important on /reload or server restarts where players may be connected before this
     * plugin finishes enabling. It is safe to call on the server thread during plugin enable.
     */
    private void initializeScoreboardsForCurrentlyOnlinePlayers() {
        int initialized = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                ScoreboardManager.onPlayerJoin(player);
                initialized++;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to initialize scoreboard for " + player.getName() + ": " + t.getMessage(), t);
            }
        }

        if (initialized > 0) {
            logger.info("Initialized scoreboards for " + initialized + " already online player(s).");
        }
    }
}
