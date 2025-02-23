package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import org.bukkit.entity.Player;

public class DistanceCondition extends PlayerVisibilityCondition {
    private final double maxDistanceSquared;

    public DistanceCondition(double maxDistance) {
        this.maxDistanceSquared = maxDistance * maxDistance;
    }

    @Override
    public boolean isVisible(Player viewer, Player target) {
        return viewer.getLocation().distanceSquared(target.getLocation()) <= maxDistanceSquared;
    }
}
