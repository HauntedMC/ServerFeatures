package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Hides the custom passenger while GSit-style seat entities are controlling the player's mount.
 */
public class GsitCondition extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        Entity vehicle = target.getVehicle();
        if (vehicle instanceof AreaEffectCloud) {
            return false;
        }

        // Preserve compatibility with older seat implementations that attached the marker inversely.
        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof AreaEffectCloud) {
                return false;
            }
        }
        return true;
    }
}
