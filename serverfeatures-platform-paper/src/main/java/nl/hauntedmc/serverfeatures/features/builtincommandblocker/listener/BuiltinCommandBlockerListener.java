package nl.hauntedmc.serverfeatures.features.builtincommandblocker.listener;

import nl.hauntedmc.serverfeatures.features.builtincommandblocker.BuiltinCommandBlocker;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal.BuiltinCommandBlockerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.Objects;

public final class BuiltinCommandBlockerListener implements Listener {

    private final BuiltinCommandBlocker feature;
    private final BuiltinCommandBlockerService service;

    public BuiltinCommandBlockerListener(BuiltinCommandBlocker feature, BuiltinCommandBlockerService service) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!service.isBlockedCommandLine(event.getMessage())) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(feature.getLocalizationHandler()
                .getMessage("builtincommandblocker.blocked")
                .forAudience(event.getPlayer())
                .build());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        boolean changed = service.refresh();
        service.removeBlockedCommands(event.getCommands());
        if (changed) {
            scheduleClientUpdate();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        scheduleRegistryRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        scheduleRegistryRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        scheduleRegistryRefresh();
    }

    private void scheduleRegistryRefresh() {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(service::refreshAndUpdatePlayers);
    }

    private void scheduleClientUpdate() {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(service::updatePlayers);
    }
}
