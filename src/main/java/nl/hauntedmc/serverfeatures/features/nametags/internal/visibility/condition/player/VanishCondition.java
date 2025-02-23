package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import org.bukkit.entity.Player;

public class VanishCondition extends PlayerVisibilityCondition {
    @Override
    public boolean isVisible(Player viewer, Player target) {
        return viewer.canSee(target);
    }
}
