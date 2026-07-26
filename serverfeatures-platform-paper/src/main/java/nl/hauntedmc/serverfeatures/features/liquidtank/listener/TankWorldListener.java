package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;

public class TankWorldListener implements Listener {

    private final LiquidTank feature;

    public TankWorldListener(LiquidTank feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent worldLoadEvent) {
        feature.getTankManager().loadUnloadedTankList(worldLoadEvent.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent worldUnloadEvent) {
        ArrayList<AbstractTank> arrayList = new ArrayList<>();
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            if (liquidTank.getLocation().getWorld() != worldUnloadEvent.getWorld())
                continue;
            arrayList.add(liquidTank);
            feature.getTankManager().getUnloadedTankList().add(new UnloadedTank(worldUnloadEvent.getWorld().getName(), liquidTank.getLocation().getBlockX(), liquidTank.getLocation().getBlockY(),
                    liquidTank.getLocation().getBlockZ(), liquidTank.getTankType(), liquidTank.getQuantity()));
        }
        for (AbstractTank liquidTank : arrayList) {
            feature.getTankManager().removeTank(liquidTank);
        }
    }
}
