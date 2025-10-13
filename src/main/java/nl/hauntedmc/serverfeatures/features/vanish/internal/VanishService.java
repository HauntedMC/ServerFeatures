package nl.hauntedmc.serverfeatures.features.vanish.internal;

import net.kyori.adventure.text.Component;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core vanish logic: state, (un)apply, notifications, actionbar ticking.
 * Uses ORM-backed persistence via VanishRepository.
 * Publishes Redis messages (if configured) on vanish state changes and on join with persisted vanish.
 */
public class VanishService {

    public static final String PERM_TOGGLE_SELF = "serverfeatures.feature.vanish.command.vanish.toggle";
    public static final String PERM_SEE = "serverfeatures.feature.vanish.see";

    private final Vanish feature;

    // In-memory runtime state for online players
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(Vanish feature) {
        this.feature = feature;
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    public boolean isPlayerVanished(Player p) {
        return p != null && isVanished(p.getUniqueId());
    }

    public Set<UUID> allVanished() {
        return Collections.unmodifiableSet(vanished);
    }

    public int countVanished() {
        return vanished.size();
    }

    /* ------------------------ Public API ------------------------ */

    public void setVanished(Player target, boolean value) {
        if (target == null || !target.isOnline()) return;

        // Enforce main thread
        if (!Bukkit.isPrimaryThread()) {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> setVanished(target, value));
            return;
        }

        boolean current = vanished.contains(target.getUniqueId());
        if (current == value) return;

        if (value) {
            vanished.add(target.getUniqueId());
            applyVanish(target);
        } else {
            vanished.remove(target.getUniqueId());
            removeVanish(target);
        }

        // Persist to DB if enabled
        try {
            feature.getRepository().upsertVanish(
                    target.getUniqueId().toString(),
                    target.getName(),
                    value
            );
        } catch (Exception ex) {
            feature.getLogger().warning("Kon vanish state niet opslaan: " + ex.getMessage());
        }

        // Publish to Redis (best-effort)
        publishVanishState(target, value);
    }

    /**
     * Called on PlayerJoinEvent to apply persisted state and notify staff.
     * Also re-enforces spectator gamemode shortly after join in case the server forces a default.
     */
    public void handleJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        boolean persistedVanished = false;

        try {
            persistedVanished = feature.getRepository().isPersistedVanished(p.getUniqueId().toString());
        } catch (Exception ex) {
            feature.getLogger().warning("Kon vanish persistentie niet lezen: " + ex.getMessage());
        }

        if (persistedVanished) {
            setVanished(p, true); // will also publish redis update and persist

            // In case default gamemode is re-applied by the server after join, enforce spectator shortly after
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (p.isOnline() && isPlayerVanished(p)) {
                    try {
                        p.setGameMode(GameMode.SPECTATOR);
                    } catch (Throwable ignored) {
                    }
                }
            }, BukkitTime.ticks(2L));

            // Notify staff that this person joined vanished (only staff with toggle perm)
            broadcastToVanishingStaff(
                    feature.getLocalizationHandler().getMessage("vanish.staff_joined_vanished")
                            .with("name", p.getName())
                            .build(),
                    p.getUniqueId()
            );
        } else {
            // Force visibility
            removeVanish(p);
        }

        // Hide all currently vanished players from the joiner (unless they can see)
        applyToNewViewer(p);
    }

    /* --------------------- Internal mechanics ------------------- */

    private void applyVanish(Player p) {
        // Hide from others who cannot see
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePairVisibility(viewer, p);
        }

        // Collisions off
        if ((boolean) feature.getConfigHandler().getSetting("disable_collisions")) {
            try {
                p.setCollidable(false);
            } catch (Throwable ignored) {
            }
        }

        // Optional invisible flag; restore safely on unvanish
        if ((boolean) feature.getConfigHandler().getSetting("set_invisible_flag")) {
            if (!p.isInvisible()) {
                p.setInvisible(true);
            }
        }

        // Force spectator gamemode while vanished
        try {
            p.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable ignored) {
        }
    }

    private void removeVanish(Player p) {
        if (!p.hasPermission(PERM_TOGGLE_SELF)) {
            return;
        }

        // Show to everyone again
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                viewer.showPlayer(feature.getPlugin(), p);
            } catch (Throwable ignored) {
            }
        }

        // Collisions off
        if ((boolean) feature.getConfigHandler().getSetting("disable_collisions")) {
            if (!p.isCollidable()) {
                p.setCollidable(true);
            }
        }

        // Optional invisible flag; restore safely on unvanish
        if ((boolean) feature.getConfigHandler().getSetting("set_invisible_flag")) {
            if (p.isInvisible()) {
                p.setInvisible(false);
            }
        }
    }

    public void applyToNewViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        final boolean canSee = viewer.hasPermission(PERM_SEE);
        if (canSee) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && v.isOnline() && !viewer.equals(v)) {
                try {
                    viewer.hidePlayer(feature.getPlugin(), v);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void updatePairVisibility(Player viewer, Player target) {
        if (viewer == null || target == null) return;
        if (!viewer.isOnline() || !target.isOnline()) return;
        if (viewer.equals(target)) return;
        boolean targetVanished = isVanished(target.getUniqueId());
        boolean viewerCanSee = viewer.hasPermission(PERM_SEE);
        try {
            if (targetVanished && !viewerCanSee) viewer.hidePlayer(feature.getPlugin(), target);
            else viewer.showPlayer(feature.getPlugin(), target);
        } catch (Throwable ignored) {
        }
    }

    /* -------------------- Notifications/UI ---------------------- */

    public void notifyStaffToggle(Player actor, Player target, boolean enabled) {
        final String key = enabled ? "vanish.staff_enabled" : "vanish.staff_disabled";
        Component msg = feature.getLocalizationHandler()
                .getMessage(key)
                .with("actor", actor != null ? actor.getName() : "Console")
                .with("target", target != null ? target.getName() : "Onbekend")
                .build();
        UUID exclude = actor != null ? actor.getUniqueId() : null;
        broadcastToVanishingStaff(msg, exclude);
    }

    public void broadcastToVanishingStaff(Component message, UUID exclude) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (exclude != null && p.getUniqueId().equals(exclude)) continue;
            if (p.hasPermission(PERM_TOGGLE_SELF)) {
                try {
                    p.sendMessage(message);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void tickActionBars() {
        // Send to vanished players only
        for (UUID id : vanished) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                try {
                    p.sendActionBar(feature.getLocalizationHandler()
                            .getMessage("vanish.actionbar").forAudience(p).build());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /* ---------------------- Shutdown safety --------------------- */

    public void cleanupOnDisable() {
        // Best-effort restore for currently online vanished players
        for (UUID id : new HashSet<>(vanished)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                removeVanish(p);
            }
        }
        vanished.clear();
    }

    /* ---------------------- Redis publish ----------------------- */

    private void publishVanishState(Player target, boolean vanished) {
        // Best-effort publish; silently skip if not configured
        EventBusHandler bus = feature.getEventBusHandler();
        if (bus == null || target == null) return;
        try {
            bus.publishState(
                    target.getUniqueId().toString(),
                    target.getName(),
                    vanished
            );
        } catch (Throwable t) {
            feature.getLogger().warning("Failed to publish vanish update for " + target.getName() + ": " + t.getMessage());
        }
    }

    public void handleLeave(PlayerQuitEvent e) {
        if (!e.getPlayer().hasPermission(PERM_TOGGLE_SELF)) {
            return;
        }
        vanished.remove(e.getPlayer().getUniqueId());
    }
}
