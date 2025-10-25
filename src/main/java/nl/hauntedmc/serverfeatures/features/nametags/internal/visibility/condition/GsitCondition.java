package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class GsitCondition extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof AreaEffectCloud) {
                return false;
            }
        }
        return true;
    }
}
