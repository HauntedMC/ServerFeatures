package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import org.bukkit.entity.Player;

public class DeathCondition extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        return !target.isDead();
    }
}
