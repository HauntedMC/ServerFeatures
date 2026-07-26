package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

public final class TransformListener implements Listener {

    private final LimitSpawnersHandler handler;

    public TransformListener(LimitSpawnersHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (event.getTransformedEntities().isEmpty()) return;

        Entity original = event.getEntity();
        Entity first = event.getTransformedEntities().getFirst();
        if (!(first instanceof LivingEntity le)) return;

        handler.transferTracking(original, le);
    }
}
