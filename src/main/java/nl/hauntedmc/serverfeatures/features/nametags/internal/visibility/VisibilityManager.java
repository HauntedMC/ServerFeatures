package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility;

import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VisibilityManager {
    private final List<PlayerVisibilityCondition> playerConditions = new ArrayList<>();
    private final Nametags feature;

    public VisibilityManager(Nametags feature) {
        this.feature = feature;
        initializePlayerConditions();
    }

    private void initializePlayerConditions() {
        playerConditions.add(new DeathCondition());
        playerConditions.add(new GsitCondition());
        playerConditions.add(new DistanceCondition((int) feature.getConfigHandler().getSetting("max_distance"))); // 64 blocks
        if (Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            playerConditions.add(new DisguiseCondition());
        }
        playerConditions.add(new VanishCondition());
        playerConditions.add(new SpectatorCondition());
    }

    public boolean isPlayerVisible(Player viewer, Nametag targetNametag) {
        for (PlayerVisibilityCondition condition : playerConditions) {
            if (!condition.isVisible(viewer, targetNametag.getNametagOwner())) {
                return false;
            }
        }
        return true;
    }

}
