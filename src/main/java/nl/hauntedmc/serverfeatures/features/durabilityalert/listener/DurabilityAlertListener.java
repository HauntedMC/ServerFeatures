package nl.hauntedmc.serverfeatures.features.durabilityalert.listener;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.durabilityalert.internal.DurabilityAlertHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

public class DurabilityAlertListener implements Listener {

    private final DurabilityAlertHandler handler;

    public DurabilityAlertListener(DurabilityAlertHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        handler.handleDamage(event);
    }
}
