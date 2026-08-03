package nl.hauntedmc.serverfeatures.features.bettercoral.listener;

import nl.hauntedmc.serverfeatures.features.bettercoral.internal.CoralMaterials;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

public final class BetterCoralListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoralFade(BlockFadeEvent event) {
        if (CoralMaterials.isDryingTransition(
                event.getBlock().getType(),
                event.getNewState().getType()
        )) {
            event.setCancelled(true);
        }
    }
}
