package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import io.papermc.paper.event.entity.ShulkerDuplicateEvent;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;

public final class TransformListener implements Listener {

    private final LimitSpawnersHandler handler;

    public TransformListener(LimitSpawnersHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!handler.reserveTransform(event.getEntity(), event.getTransformedEntities())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransformFinalState(EntityTransformEvent event) {
        handler.scheduleTransformFinalization(event.getEntity(), event::isCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlimeSplit(SlimeSplitEvent event) {
        int accepted = handler.prepareSlimeSplit(event.getEntity(), event.getCount());
        if (accepted <= 0) {
            event.setCancelled(true);
        } else if (accepted != event.getCount()) {
            event.setCount(accepted);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShulkerDuplicate(ShulkerDuplicateEvent event) {
        if (!handler.tryReserveShulkerChild(event.getParent(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShulkerDuplicateFinalState(ShulkerDuplicateEvent event) {
        handler.scheduleSpawnFinalization(event.getEntity(), event::isCancelled);
    }
}
