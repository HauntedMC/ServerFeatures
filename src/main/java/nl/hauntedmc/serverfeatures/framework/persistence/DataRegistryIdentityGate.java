package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs Bukkit feature work after DataRegistry has prepared a player's canonical identity.
 */
public final class DataRegistryIdentityGate {

    private DataRegistryIdentityGate() {
    }

    /**
     * Waits for DataRegistry identity readiness, then schedules the action on the Bukkit main thread.
     *
     * @param feature       owning feature, used for task tracking and logging.
     * @param player        player whose identity is required.
     * @param action        work to run after the identity is ready.
     * @param operationName short name included in failure logs.
     */
    public static void runWhenReady(
            BukkitBaseFeature<?> feature,
            Player player,
            Consumer<Player> action,
            String operationName
    ) {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(action, "action must not be null");
        UUID playerUuid = player.getUniqueId();
        PlayerDirectory directory = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for " + operationName + "."))
                .getPlayerDirectory();

        directory.whenReady(playerUuid).whenComplete((identity, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning(
                        "DataRegistry identity was unavailable for " + operationName + ": " + throwable.getMessage()
                );
                return;
            }
            if (identity == null || identity.isEmpty()) {
                return;
            }
            if (!feature.getPlugin().isEnabled()) {
                return;
            }
            try {
                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                    if (!feature.getPlugin().isEnabled()) {
                        return;
                    }
                    if (player.isOnline() && player.getUniqueId().equals(playerUuid)) {
                        action.accept(player);
                    }
                });
            } catch (RuntimeException exception) {
                feature.getLogger().warning(
                        "Could not schedule DataRegistry-ready task for " + operationName + ": "
                                + exception.getMessage()
                );
            }
        });
    }
}
