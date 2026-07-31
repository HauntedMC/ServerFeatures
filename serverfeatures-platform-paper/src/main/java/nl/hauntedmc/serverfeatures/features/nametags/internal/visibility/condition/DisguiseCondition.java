package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import me.libraryaddict.disguise.DisguiseAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DisguiseCondition extends PlayerVisibilityCondition {

    @Override
    public boolean isVisible(Player viewer, Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            return true;
        }
        return !DisguiseAPI.isDisguised(target);
    }
}
