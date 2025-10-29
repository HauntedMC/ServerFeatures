package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class ParcourDeathVoidListener implements Listener {

    private final ParcourHandler handler;
    private final Parcour feature;


    public ParcourDeathVoidListener(Parcour feature, ParcourHandler handler) {
        this.handler = handler;
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player p)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (handler.isPlaying(p)) {
                // Prevent death; send back to checkpoint
                event.setCancelled(true);
                handler.onPlayerDeathOrVoid(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        var p = event.getPlayer();
        if (handler.isPlaying(p)) {
            // Redirect respawn to last checkpoint
            handler.session(p).ifPresent(s -> {
                if (s.restoreLocation() != null) {
                    event.setRespawnLocation(s.restoreLocation());
                }
            });
            // After respawn, ensure teleport ignore window to avoid insta triggers
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> handler.onPlayerDeathOrVoid(p)
            );
        }
    }
}
