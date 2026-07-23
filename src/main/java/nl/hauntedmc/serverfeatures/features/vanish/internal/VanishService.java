package nl.hauntedmc.serverfeatures.features.vanish.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.vanish.internal.messaging.EventBusHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishService {

    public static final String PERM_TOGGLE_SELF = "serverfeatures.feature.vanish.command.vanish.toggle";
    public static final String PERM_SEE = "serverfeatures.feature.vanish.see";

    private final Vanish feature;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerIds = new ConcurrentHashMap<>();

    public VanishService(Vanish feature) {
        this.feature = feature;
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    public boolean isPlayerVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }

    public Set<UUID> allVanished() {
        return Collections.unmodifiableSet(vanished);
    }

    public int countVanished() {
        return vanished.size();
    }

    public void setVanished(Player target, boolean value) {
        if (target == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> setVanished(target, value));
            return;
        }
        if (!target.isOnline()) {
            return;
        }

        setVanishedInternal(target, value, true);
    }

    private void setVanishedInternal(Player target, boolean value, boolean persist) {
        UUID playerUuid = target.getUniqueId();
        boolean current = vanished.contains(playerUuid);
        if (current == value) {
            return;
        }

        if (value) {
            vanished.add(playerUuid);
            applyVanish(target);
        } else {
            vanished.remove(playerUuid);
            removeVanish(target);
        }

        if (persist) {
            Long playerId = playerIds.get(playerUuid);
            if (playerId == null) {
                playerId = feature.getRepository().findExistingPlayerId(playerUuid.toString());
                if (playerId != null) {
                    playerIds.put(playerUuid, playerId);
                }
            }
            if (playerId != null) {
                long stablePlayerId = playerId;
                feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(
                        () -> feature.getRepository().upsertVanish(stablePlayerId, value)
                );
            } else {
                feature.getLogger().warning("Kon vanish state niet opslaan: DataRegistry identity ontbreekt voor "
                        + playerUuid);
            }
        }
        publishVanishState(target, value);
    }

    public void handleJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    public void handleJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Long playerId = feature.getRepository().findExistingPlayerId(player.getUniqueId().toString());
        if (playerId == null) {
            applyJoinState(player.getUniqueId(), false);
            return;
        }
        handleJoin(player, new PlayerIdentity(playerId, player.getUniqueId(), player.getName()));
    }

    public void handleJoin(Player player, PlayerIdentity identity) {
        if (player == null || !player.isOnline() || identity == null || identity.playerId() <= 0L) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        playerIds.put(playerUuid, identity.playerId());
        feature.getLifecycleManager().getTaskManager().supplyAsync(
                () -> feature.getRepository().isPersistedVanished(identity.playerId())
        ).whenComplete((persistedVanished, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Kon vanish persistentie niet lezen: " + rootMessage(throwable));
                return;
            }
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                    () -> applyJoinState(playerUuid, Boolean.TRUE.equals(persistedVanished))
            );
        });
    }

    private void applyJoinState(UUID playerUuid, boolean persistedVanished) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (persistedVanished) {
            setVanishedInternal(player, true, false);
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                Player current = Bukkit.getPlayer(playerUuid);
                if (current != null && current.isOnline() && isPlayerVanished(current)) {
                    try {
                        current.setGameMode(GameMode.SPECTATOR);
                    } catch (Throwable ignored) {
                    }
                }
            }, BukkitTime.ticks(2L));

            broadcastToVanishingStaff(
                    feature.getLocalizationHandler().getMessage("vanish.staff_joined_vanished")
                            .with("name", player.getName())
                            .build(),
                    playerUuid
            );
        } else {
            vanished.remove(playerUuid);
            removeVanish(player);
        }
        applyToNewViewer(player);
    }

    private void applyVanish(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePairVisibility(viewer, player);
        }
        if ((boolean) feature.getConfigHandler().get("disable_collisions")) {
            try {
                player.setCollidable(false);
            } catch (Throwable ignored) {
            }
        }
        if ((boolean) feature.getConfigHandler().get("set_invisible_flag") && !player.isInvisible()) {
            player.setInvisible(true);
        }
        try {
            player.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable ignored) {
        }
    }

    private void removeVanish(Player player) {
        if (!player.hasPermission(PERM_TOGGLE_SELF)) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                viewer.showPlayer(feature.getPlugin(), player);
            } catch (Throwable ignored) {
            }
        }
        if ((boolean) feature.getConfigHandler().get("disable_collisions") && !player.isCollidable()) {
            player.setCollidable(true);
        }
        if ((boolean) feature.getConfigHandler().get("set_invisible_flag") && player.isInvisible()) {
            player.setInvisible(false);
        }
    }

    public void applyToNewViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.hasPermission(PERM_SEE)) {
            return;
        }
        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer != null && vanishedPlayer.isOnline() && !viewer.equals(vanishedPlayer)) {
                try {
                    viewer.hidePlayer(feature.getPlugin(), vanishedPlayer);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void updatePairVisibility(Player viewer, Player target) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline() || viewer.equals(target)) {
            return;
        }
        try {
            if (isVanished(target.getUniqueId()) && !viewer.hasPermission(PERM_SEE)) {
                viewer.hidePlayer(feature.getPlugin(), target);
            } else {
                viewer.showPlayer(feature.getPlugin(), target);
            }
        } catch (Throwable ignored) {
        }
    }

    public void notifyStaffToggle(Player actor, Player target, boolean enabled) {
        String key = enabled ? "vanish.staff_enabled" : "vanish.staff_disabled";
        Component message = feature.getLocalizationHandler()
                .getMessage(key)
                .with("actor", actor != null ? actor.getName() : "Console")
                .with("target", target != null ? target.getName() : "Onbekend")
                .build();
        broadcastToVanishingStaff(message, actor != null ? actor.getUniqueId() : null);
    }

    public void broadcastToVanishingStaff(Component message, UUID exclude) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if ((exclude == null || !player.getUniqueId().equals(exclude)) && player.hasPermission(PERM_TOGGLE_SELF)) {
                try {
                    player.sendMessage(message);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void tickActionBars() {
        for (UUID id : vanished) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                try {
                    player.sendActionBar(feature.getLocalizationHandler()
                            .getMessage("vanish.actionbar")
                            .forAudience(player)
                            .build());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void cleanupOnDisable() {
        for (UUID id : new HashSet<>(vanished)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                removeVanish(player);
            }
        }
        vanished.clear();
        playerIds.clear();
    }

    private void publishVanishState(Player target, boolean value) {
        EventBusHandler bus = feature.getEventBusHandler();
        if (bus == null || target == null) {
            return;
        }
        try {
            bus.publishState(target.getUniqueId().toString(), target.getName(), value);
        } catch (Throwable throwable) {
            feature.getLogger().warning("Failed to publish vanish update for " + target.getName() + ": "
                    + throwable.getMessage());
        }
    }

    public void handleLeave(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        vanished.remove(playerUuid);
        playerIds.remove(playerUuid);
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
