package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import org.bukkit.entity.Player;
import me.libraryaddict.disguise.DisguiseAPI;

public class DisguiseCondition  extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        return !DisguiseAPI.isDisguised(target.getPlayer());
    }
}
