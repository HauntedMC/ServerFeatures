package nl.hauntedmc.serverfeatures.features.portals.listener;

import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import nl.hauntedmc.serverfeatures.features.portals.model.Region;
import nl.hauntedmc.serverfeatures.features.portals.registry.PortalRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

public final class PortalOverrideListener implements Listener {

    private final PortalsHandler handler;
    private final PortalRegistry registry;

    public PortalOverrideListener(Portals feature, PortalsHandler handler) {
        this.handler = handler;
        this.registry = feature.getRegistry();
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // If the player is inside one of our plugin-defined portal regions, cancel vanilla and handle ourselves
        var from = event.getFrom();
        boolean inside = registry.all().stream()
                .flatMap(def -> def.region().stream())
                .anyMatch((Region r) -> r.worldName().equals(from.getWorld().getName()) && r.contains(from));

        if (inside) {
            event.setCancelled(true);
            handler.tryTrigger(event.getPlayer(), from);
        }
    }
}
