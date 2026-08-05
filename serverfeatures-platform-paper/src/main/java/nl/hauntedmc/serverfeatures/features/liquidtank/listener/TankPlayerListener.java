package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TankPlayerListener implements Listener {

    private final LiquidTank feature;

    public TankPlayerListener(LiquidTank feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        feature.getTankManager().refreshPlayerView(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        if (feature.getTankManager().hasTankInChunk(event.getChunk())) {
            scheduleChunkRefresh(event.getPlayer(), event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        feature.getTankManager().forgetPlayer(event.getPlayer());
    }

    private void scheduleRefresh(Player player) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (player.isOnline()) {
                feature.getTankManager().refreshPlayerView(player);
            }
        }, BukkitTime.ticks(1L));
    }

    private void scheduleChunkRefresh(Player player, Chunk chunk) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (player.isOnline()) {
                feature.getTankManager().refreshPlayerChunk(player, chunk);
            }
        }, BukkitTime.ticks(1L));
    }
}
