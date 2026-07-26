package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import org.bukkit.entity.Player;

public class DistanceCondition extends PlayerVisibilityCondition {
    private final int maxDistanceSquared;

    public DistanceCondition(int maxDistance) {
        this.maxDistanceSquared = maxDistance * maxDistance;
    }

    @Override
    public boolean isVisible(Player viewer, Player target) {
        if (viewer == null || target == null) return false;
        if (viewer.getWorld() != target.getWorld()) return false;
        return viewer.getLocation().distanceSquared(target.getLocation()) <= maxDistanceSquared;
    }
}
