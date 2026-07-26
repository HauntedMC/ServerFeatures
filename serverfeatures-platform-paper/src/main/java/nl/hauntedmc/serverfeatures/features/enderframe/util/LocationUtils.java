package nl.hauntedmc.serverfeatures.features.enderframe.util;

import io.papermc.paper.math.Position;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.structure.Structure;

public class LocationUtils {

    public static boolean isInStronghold(Block block) {
        World world = block.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        return world.hasStructureAt(Position.block(block.getLocation()), Structure.STRONGHOLD);
    }
}
