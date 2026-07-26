package nl.hauntedmc.serverfeatures.features.portals.listener;

import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class PortalsListener implements Listener {

    private final PortalsHandler handler;

    public PortalsListener(PortalsHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player p = event.getPlayer();
        handler.tryTrigger(p, event.getTo());
    }
}
