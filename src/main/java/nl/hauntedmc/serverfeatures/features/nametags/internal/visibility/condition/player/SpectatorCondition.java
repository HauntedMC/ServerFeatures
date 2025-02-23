package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class SpectatorCondition extends PlayerVisibilityCondition {
    @Override
    public boolean isVisible(Player viewer, Player target) {
        return target.getGameMode() != GameMode.SPECTATOR;
    }
}
