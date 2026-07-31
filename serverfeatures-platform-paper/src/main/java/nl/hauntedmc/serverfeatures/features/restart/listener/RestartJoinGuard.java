package nl.hauntedmc.serverfeatures.features.restart.listener;

import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/** Prevents the backend population from growing after the restart drain has been committed. */
public final class RestartJoinGuard implements Listener {

    private final Restart feature;
    private final RestartService service;

    public RestartJoinGuard(Restart feature, RestartService service) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConnectionValidate(PlayerConnectionValidateLoginEvent event) {
        if (!service.isAcceptingJoins()) {
            event.kickMessage(joinBlockedMessage(null));
        }
    }

    /**
     * Defensive fallback for connections already past validation when the drain gate closes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!service.isAcceptingJoins()) {
            event.getPlayer().kick(joinBlockedMessage(event.getPlayer()));
        }
    }

    private Component joinBlockedMessage(Player player) {
        var builder = feature.getLocalizationHandler().getMessage("restart.join_blocked");
        if (player != null) {
            builder.forAudience(player);
        }
        return builder.build();
    }
}
