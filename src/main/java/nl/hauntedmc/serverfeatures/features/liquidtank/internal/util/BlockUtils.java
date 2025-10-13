package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import org.bukkit.Location;

public class BlockUtils {
    public static boolean isLoaded(Location paramLocation) {
        int i = paramLocation.getBlockX() >> 4;
        int j = paramLocation.getBlockZ() >> 4;
        return paramLocation.getWorld().isChunkLoaded(i, j);
    }
}
