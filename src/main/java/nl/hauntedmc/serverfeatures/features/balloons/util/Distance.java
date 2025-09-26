package nl.hauntedmc.serverfeatures.features.balloons.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Simple leash-follow behaviour to keep the parrot near the player,
 * and the armor stand aligned underneath.
 */
public final class Distance {

    private Distance() {}

    public static void line(Entity parrot, Player player) {
        if (parrot.getWorld() != player.getWorld()) return;

        double d = parrot.getLocation().distance(player.getLocation());
        if (d > 3.8D) {
            Vector direction = player.getLocation().toVector().subtract(parrot.getLocation().toVector()).normalize();
            parrot.setVelocity(parrot.getVelocity().add(direction.multiply(0.2D)));
        }
        if (d < 3.0D) {
            parrot.setVelocity(parrot.getVelocity().add(new Vector(0.0D, 0.3D, 0.0D)));
        }
        parrot.getLocation().setDirection(player.getLocation().getDirection());
    }
}
