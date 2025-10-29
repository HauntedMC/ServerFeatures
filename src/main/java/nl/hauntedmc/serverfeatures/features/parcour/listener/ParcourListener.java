package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class ParcourListener implements Listener {

    private final ParcourHandler handler;

    public ParcourListener(ParcourHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Ignore micro-moves within same block to reduce spam
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player p = event.getPlayer();

        if (handler.isMovementFrozen(p)) {
            // Hard-freeze movement during countdown
            event.setTo(event.getFrom());
            return;
        }

        // Use swept AABB between from->to so high-speed elytra movement cannot skip regions.
        handler.tryTrigger(p, event.getFrom(), event.getTo());
    }
}
