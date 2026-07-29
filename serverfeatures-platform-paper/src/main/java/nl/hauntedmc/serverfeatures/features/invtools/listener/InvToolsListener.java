package nl.hauntedmc.serverfeatures.features.invtools.listener;

import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import net.kyori.adventure.text.Component;
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

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class InvToolsListener implements Listener {

    private final InvTools feature;
    private final InvToolsService service;
    private final Function<String, Component> migrationLoginMessage;

    public InvToolsListener(InvTools feature, InvToolsService service) {
        this(
                feature,
                service,
                playerName -> feature.getLocalizationHandler()
                        .getMessage("invtools.login_migration")
                        .with("player", playerName)
                        .build()
        );
    }

    InvToolsListener(
            InvTools feature,
            InvToolsService service,
            Function<String, Component> migrationLoginMessage
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.service = Objects.requireNonNull(service, "service");
        this.migrationLoginMessage = Objects.requireNonNull(
                migrationLoginMessage,
                "migrationLoginMessage"
        );
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
        UUID playerId = event.getUniqueId();
        if (feature.getMigrationCoordinator().blocksLogin(playerId)) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    migrationLoginMessage.apply(event.getName())
            );
            return;
        }
        if (service.prepareLogin(playerId) == InvToolsService.LoginBarrierResult.RETRY) {
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
        UUID playerId = event.getConnection().getProfile().getId();
        if (feature.getMigrationCoordinator().blocksLogin(playerId)) {
            event.getConnection().disconnect(migrationLoginMessage.apply(
                    event.getConnection().getProfile().getName()
            ));
            return;
        }
        service.handlePlayerDataLoad(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        service.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleTargetQuit(event.getPlayer());
        service.handleViewerDisconnect(event.getPlayer());
    }
}
