package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

public final class TransformListener implements Listener {

    private final LimitSpawnersHandler handler;

    public TransformListener(LimitSpawnersHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        handler.transferTracking(event.getEntity(), event.getTransformedEntities());
    }
}
