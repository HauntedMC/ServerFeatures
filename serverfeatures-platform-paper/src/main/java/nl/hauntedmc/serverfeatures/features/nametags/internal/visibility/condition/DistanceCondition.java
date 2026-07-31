package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import org.bukkit.entity.Player;

public class DistanceCondition extends PlayerVisibilityCondition {
    private final double maxDistanceSquared;

    public DistanceCondition(int maxDistance) {
        double safeDistance = Math.max(1, maxDistance);
        this.maxDistanceSquared = safeDistance * safeDistance;
    }

    @Override
    public boolean isVisible(Player viewer, Player target) {
        if (viewer == null || target == null || viewer.getWorld() != target.getWorld()) {
            return false;
        }
        return viewer.getLocation().distanceSquared(target.getLocation()) <= maxDistanceSquared;
    }
}
