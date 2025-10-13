package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import me.libraryaddict.disguise.DisguiseAPI;
import org.bukkit.entity.Player;

public class DisguiseCondition extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        return !DisguiseAPI.isDisguised(target.getPlayer());
    }
}
