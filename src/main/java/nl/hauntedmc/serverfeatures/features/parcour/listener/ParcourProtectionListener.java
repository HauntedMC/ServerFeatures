package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.Optional;

public final class ParcourProtectionListener implements Listener {

    private final Parcour feature;
    private final ParcourHandler handler;

    public ParcourProtectionListener(Parcour feature, ParcourHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    private Optional<ParcourDefinition> defFor(Player p) {
        return handler.session(p).flatMap(s -> feature.getRegistry().get(s.parcourId));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!handler.isPlaying(p)) return;

        var defOpt = defFor(p);
        if (defOpt.isEmpty()) return;

        if (!defOpt.get().hungerEnabled()) {
            event.setCancelled(true);
            // keep topped up
            p.setFoodLevel(20);
            p.setSaturation(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!handler.isPlaying(p)) return;

        var defOpt = defFor(p);
        if (defOpt.isEmpty()) return;

        // Let VOID be handled by the dedicated void listener (teleport to checkpoint),
        // otherwise cancel damage if disabled.
        if (!defOpt.get().damageEnabled() && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!handler.isPlaying(p)) return;

        // Keep the inventory to avoid dropping control items; snapshot will restore on respawn/leave/finish
        event.setKeepInventory(true);
        event.getDrops().clear();

        // Avoid losing XP from snapshot perspective
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}
