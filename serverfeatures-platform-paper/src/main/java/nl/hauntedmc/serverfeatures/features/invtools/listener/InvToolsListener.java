package nl.hauntedmc.serverfeatures.features.invtools.listener;

import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public final class InvToolsListener implements Listener {

    private final InvTools feature;
    private final InvToolsService service;

    public InvToolsListener(InvTools feature, InvToolsService service) {
        this.feature = feature;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        service.handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof InvToolsView view)) {
            return;
        }
        if (view.isolatesViewerCursor()
                || event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        service.handleInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (service.prepareLogin(event.getUniqueId()) == InvToolsService.LoginBarrierResult.RETRY) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    feature.getLocalizationHandler()
                            .getMessage("invtools.login_retry")
                            .with("player", event.getName())
                            .build()
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        service.handlePlayerDataLoad(event.getConnection().getProfile().getId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        service.handlePlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleTargetQuit(event.getPlayer());
        service.handleViewerDisconnect(event.getPlayer());
    }
}
