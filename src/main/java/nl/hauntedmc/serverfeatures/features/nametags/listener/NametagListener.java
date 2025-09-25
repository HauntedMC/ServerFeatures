package nl.hauntedmc.serverfeatures.features.nametags.listener;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

public class NametagListener implements Listener {

    private final Nametags feature;

    public NametagListener(Nametags feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Delay so client has loaded and can accept passengers reliably
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> feature.getNametagManager().respawn(e.getPlayer()),
                BukkitTime.ticks(10L)
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feature.getNametagManager().remove(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        // Passengers don't cross worlds—recreate in target world
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> feature.getNametagManager().respawn(e.getPlayer()),
                BukkitTime.ticks(10L)
        );
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // Optional: ensure clean slate; respawn event will rebuild
        feature.getNametagManager().remove(e.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> feature.getNametagManager().respawn(e.getPlayer()),
                BukkitTime.ticks(10L)
        );
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        feature.getNametagManager().handleTeleport(e.getPlayer());
    }
}
