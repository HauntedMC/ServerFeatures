package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility;

import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.DeathCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.DisguiseCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.DistanceCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.GsitCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.PlayerVisibilityCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.SpectatorCondition;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition.VanishCondition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates all owner/viewer visibility rules without allowing one optional integration failure to
 * terminate the repeating reconciliation task.
 */
public final class VisibilityManager {
    private static final long FAILURE_LOG_INTERVAL_NANOS = 30_000_000_000L;

    private final List<PlayerVisibilityCondition> playerConditions = new ArrayList<>();
    private final Map<Class<?>, Long> lastFailureLogNanos = new HashMap<>();
    private final Nametags feature;

    public VisibilityManager(Nametags feature) {
        this.feature = feature;
        initializePlayerConditions();
    }

    private void initializePlayerConditions() {
        playerConditions.add(new DeathCondition());
        playerConditions.add(new GsitCondition());
        playerConditions.add(new DistanceCondition(maxDistance()));
        if (Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            playerConditions.add(new DisguiseCondition());
        }
        playerConditions.add(new VanishCondition());
        playerConditions.add(new SpectatorCondition());
    }

    public boolean isPlayerVisible(Player viewer, Nametag targetNametag) {
        Player owner = targetNametag.getNametagOwner();
        for (PlayerVisibilityCondition condition : playerConditions) {
            try {
                if (!condition.isVisible(viewer, owner)) {
                    return false;
                }
            } catch (RuntimeException | LinkageError exception) {
                logConditionFailure(condition, viewer, owner, exception);
                // Fail closed: a broken vanish/disguise integration must never expose a hidden player.
                return false;
            }
        }
        return true;
    }

    private int maxDistance() {
        Object raw = feature.getConfigHandler().get("max_distance");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 45;
    }

    private void logConditionFailure(
            PlayerVisibilityCondition condition,
            Player viewer,
            Player owner,
            Throwable exception
    ) {
        long now = System.nanoTime();
        Class<?> conditionType = condition.getClass();
        Long lastLog = lastFailureLogNanos.get(conditionType);
        if (lastLog != null && now - lastLog < FAILURE_LOG_INTERVAL_NANOS) {
            return;
        }
        lastFailureLogNanos.put(conditionType, now);

        feature.getLogger().warning(
                "Nametag visibility condition " + conditionType.getSimpleName() + " failed for viewer "
                        + safeName(viewer) + " and owner " + safeName(owner) + ": " + rootMessage(exception)
        );
    }

    private static String safeName(Player player) {
        return player == null ? "<null>" : player.getName();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
