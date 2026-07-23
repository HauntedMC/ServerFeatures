package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Runs Bukkit feature work after DataRegistry has prepared a player's canonical identity.
 */
public final class DataRegistryIdentityGate {

    private DataRegistryIdentityGate() {
    }

    /**
     * Waits for DataRegistry identity initialization, then schedules the action on the Bukkit main thread.
     */
    public static void runWhenReady(
            BukkitBaseFeature<?> feature,
            Player player,
            Consumer<Player> action,
            String operationName
    ) {
        Objects.requireNonNull(action, "action must not be null");
        runWhenReady(feature, player, (readyPlayer, identity) -> {
            if (identity.playerId() > 0L) {
                action.accept(readyPlayer);
            }
        }, operationName);
    }

    /**
     * Waits for DataRegistry identity initialization and preserves the resolved immutable identity for the
     * downstream action instead of forcing consumers to perform another cache lookup.
     */
    public static void runWhenReady(
            BukkitBaseFeature<?> feature,
            Player player,
            BiConsumer<Player, PlayerIdentity> action,
            String operationName
    ) {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(operationName, "operationName must not be null");

        UUID playerUuid = player.getUniqueId();
        PlayerData players = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for " + operationName + "."))
                .players();

        players.whenReady(playerUuid).whenComplete((identity, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning(
                        "DataRegistry identity was unavailable for " + operationName + ": " + rootMessage(throwable)
                );
                return;
            }
            if (identity == null || identity.isEmpty()) {
                return;
            }

            PlayerIdentity resolvedIdentity = identity.get();
            try {
                feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                    if (!feature.getPlugin().isEnabled()) {
                        return;
                    }
                    if (player.isOnline() && player.getUniqueId().equals(playerUuid)) {
                        action.accept(player, resolvedIdentity);
                    }
                });
            } catch (RuntimeException exception) {
                feature.getLogger().warning(
                        "Could not schedule DataRegistry-ready task for " + operationName + ": "
                                + rootMessage(exception)
                );
            }
        });
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
