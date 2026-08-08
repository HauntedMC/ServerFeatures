package nl.hauntedmc.serverfeatures.features.phantomtoggle.listener;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.PhantomToggle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.UUID;

public final class PhantomSpawnListener implements Listener {

    private final PhantomToggle feature;

    public PhantomSpawnListener(PhantomToggle feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhantomPreSpawn(PhantomPreSpawnEvent event) {
        if (!(event.getSpawningEntity() instanceof Player player)) {
            return;
        }
        if (!feature.preferences().shouldSuppressSpawn(player)) {
            return;
        }

        event.setCancelled(true);
        event.setShouldAbortSpawn(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                || !(event.getEntity() instanceof Phantom phantom)) {
            return;
        }

        UUID spawningEntityId = phantom.getSpawningEntity();
        if (spawningEntityId == null) {
            return;
        }

        Entity spawningEntity = phantom.getWorld().getEntity(spawningEntityId);
        if (spawningEntity instanceof Player player
                && feature.preferences().shouldSuppressSpawn(player)) {
            event.setCancelled(true);
        }
    }
}
