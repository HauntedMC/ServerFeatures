package nl.hauntedmc.serverfeatures.features.holograms.listener;

import nl.hauntedmc.serverfeatures.features.holograms.Holograms;
import nl.hauntedmc.serverfeatures.features.holograms.model.HologramDefinition;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class HologramListener implements Listener {

    private final Holograms feature;

    public HologramListener(Holograms feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        World world = e.getWorld();
        for (HologramDefinition def : feature.getRegistry().all()) {
            if (def.worldName.equalsIgnoreCase(world.getName())) {
                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(feature.getHandler()::spawnAllSafe);
                break;
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        feature.getHandler().removeAll();
    }
}
