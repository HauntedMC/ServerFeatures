package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class TankPlayerListener implements Listener {

    private final LiquidTank feature;

    public TankPlayerListener(LiquidTank feature) {
        this.feature = feature;
    }


    @EventHandler
    public void onTeleport(PlayerTeleportEvent playerTeleportEvent) {
        try {
            feature.getTankManager().loadUnloadedTankList(playerTeleportEvent.getTo().getWorld());
        } catch (Exception exception) {
            // empty catch block
        }
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            liquidTank.updatePlayerView();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            feature.getTankManager().loadUnloadedTankList(playerJoinEvent.getPlayer().getWorld());
            for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
                liquidTank.updatePlayerView(playerJoinEvent.getPlayer());
            }
        }, BukkitTime.ticks(0L));
    }


    @EventHandler
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            liquidTank.updatePlayerView(playerMoveEvent.getPlayer());
        }
    }

}
