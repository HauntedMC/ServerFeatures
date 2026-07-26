package nl.hauntedmc.serverfeatures.features.versionrecommender.internal;

import nl.hauntedmc.serverfeatures.api.hook.ViaVersionHook;
import nl.hauntedmc.serverfeatures.features.versionrecommender.VersionRecommender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Core comparison + messaging logic. Reads and caches its own config flags in the constructor.
 */
public final class RecommendationService {

    private final VersionRecommender feature;
    private final ViaVersionHook via;

    private final boolean warnOlder;
    private final boolean warnNewer;

    public RecommendationService(VersionRecommender feature, ViaVersionHook via) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.via = Objects.requireNonNull(via, "via");
        this.warnOlder = (boolean) feature.getConfigHandler().get("warn_players_older");
        this.warnNewer = (boolean) feature.getConfigHandler().get("warn_players_newer");
    }

    public void recommendIfNeeded(Player player) {
        if (player == null || !player.isOnline() || !via.isAvailable()) return;

        final int serverId = via.getServerNativeProtocolId();
        final String serverName = via.getServerNativeProtocolName();
        final int clientId = via.getClientProtocolId(player);
        final String clientName = via.getClientProtocolName(player);

        if ("UNKNOWN".equalsIgnoreCase(serverName)) return;

        if (clientId < serverId && warnOlder) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("versionrecommender.warn-older")
                            .with("version", clientName)
                            .with("server", serverName)
                            .forAudience(player)
                            .build()
            );
            return;
        }

        if (clientId > serverId && warnNewer) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("versionrecommender.warn-newer")
                            .with("version", clientName)
                            .with("server", serverName)
                            .forAudience(player)
                            .build()
            );
        }
    }
}
