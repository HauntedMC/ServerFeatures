package nl.hauntedmc.serverfeatures.features.itemedit.listener;

import nl.hauntedmc.serverfeatures.features.itemedit.ItemEdit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;

public class AnvilListener implements Listener {

    private final ItemEdit feature;

    public AnvilListener(ItemEdit feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        feature.getItemHandler().renameItemInAnvil(event);
    }
}
