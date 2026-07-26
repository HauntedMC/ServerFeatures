package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.VisibilityCondition;
import org.bukkit.entity.Player;

public abstract class PlayerVisibilityCondition implements VisibilityCondition {
    public abstract boolean isVisible(Player viewer, Player target);
}
