package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ParcourQuitListener implements Listener {

    private final ParcourHandler handler;

    public ParcourQuitListener(ParcourHandler handler) {
        this.handler = handler;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handler.onQuit(event.getPlayer());
    }
}
