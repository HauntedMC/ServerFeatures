package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility;

import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.nametag.NametagVisibilityCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player.GsitCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.player.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VisibilityManager {
    private final List<PlayerVisibilityCondition> playerConditions = new ArrayList<>();
    private final List<NametagVisibilityCondition> nametagConditions = new ArrayList<>();

    public VisibilityManager() {
        initializePlayerConditions();
        initializeNametagConditions();
    }

    private void initializeNametagConditions() {
    }

    private void initializePlayerConditions() {
        playerConditions.add(new GsitCondition());
        playerConditions.add(new DeathCondition());
        playerConditions.add(new WorldCondition());
        playerConditions.add(new DistanceCondition(64)); // 64 blocks
        playerConditions.add(new VanishCondition());
        playerConditions.add(new SpectatorCondition());
    }

    public boolean isPlayerVisible(Player viewer, Nametag targetNametag) {
        for (PlayerVisibilityCondition condition : playerConditions) {
            if (!condition.isVisible(viewer, targetNametag.getNametagOwner())) {
                return false;
            }
        }
        for (NametagVisibilityCondition condition : nametagConditions) {
            if (!condition.isVisible(targetNametag)) {
                return false;
            }
        }
        return true;
    }

    public void addCondition(PlayerVisibilityCondition condition) {
        playerConditions.add(condition);
    }

    public void removeCondition(PlayerVisibilityCondition condition) {
        playerConditions.remove(condition);
    }
}
