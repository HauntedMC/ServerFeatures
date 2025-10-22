package nl.hauntedmc.serverfeatures.api.ui.hud.toast;

import nl.hauntedmc.serverfeatures.api.effect.sound.SoundProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;

public final class ToastAPI {

    private ToastAPI() {
    }

    public enum Frame {TASK, GOAL, CHALLENGE}

    /**
     * Full-control toast:
     * - Creates/replaces advancement at {@code keyPath}
     * - Awards it (shows toast)
     * - Plays sound ONLY if {@code soundProfile != null}
     * - Revokes after {@code revokeDelayTicks}; optionally removes the advancement
     * NOTE: {@code description} is intentionally ignored for the toast (title-only).
     * An empty description is written to satisfy the advancement schema.
     * Safe to call from async; everything is scheduled on main.
     */
    public static void showToast(
            Plugin plugin,
            Player player,
            String keyPath,
            String title,
            Material icon,
            Frame frame,
            long revokeDelayTicks,
            boolean removeAfter,
            SoundProfile soundProfile
    ) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) return;

            NamespacedKey key = new NamespacedKey(plugin, sanitizePath(keyPath));
            String iconKey = (icon == null ? Material.PAPER : icon).getKey().toString();
            String frameStr = switch (frame) {
                case GOAL -> "goal";
                case CHALLENGE -> "challenge";
                default -> "task";
            };

            // Modern schema (1.20.5+): icon.id; description kept empty for validity
            String json = """
                    {
                      "display": {
                        "icon": { "id": "%s" },
                        "title": %s,
                        "description": { "text": "" },
                        "frame": "%s",
                        "announce_to_chat": false,
                        "show_toast": true,
                        "hidden": true
                      },
                      "criteria": { "impossible": { "trigger": "minecraft:impossible" } }
                    }
                    """.formatted(iconKey, title, frameStr);

            @SuppressWarnings("deprecation")
            var unsafe = Bukkit.getUnsafe();
            try {
                unsafe.loadAdvancement(key, json);
            } catch (IllegalArgumentException exists) {
                try {
                    @SuppressWarnings("deprecation") var _u = Bukkit.getUnsafe();
                    _u.removeAdvancement(key);
                } catch (Throwable ignored) {
                }
                unsafe.loadAdvancement(key, json);
            }

            Advancement adv = Bukkit.getAdvancement(key);
            if (adv == null) return;

            // Show the toast
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String c : progress.getAwardedCriteria()) progress.revokeCriteria(c);
            progress.awardCriteria("impossible");

            // Optional sound
            if (soundProfile != null) {
                soundProfile.play(player);
            }

            // Revoke later; optionally remove
            long delay = Math.max(1L, revokeDelayTicks);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Advancement adv0 = Bukkit.getAdvancement(key);
                if (adv0 != null) {
                    AdvancementProgress p0 = player.getAdvancementProgress(adv0);
                    for (String c : p0.getAwardedCriteria()) p0.revokeCriteria(c);
                }
                if (removeAfter) {
                    try {
                        @SuppressWarnings("deprecation") var _u = Bukkit.getUnsafe();
                        _u.removeAdvancement(key);
                    } catch (Throwable ignored) {
                    }
                }
            }, delay);
        });
    }

    /**
     * Overload: no sound (passes null).
     */
    public static void showToast(
            Plugin plugin,
            Player player,
            String keyPath,
            String title,
            Material icon,
            Frame frame,
            long revokeDelayTicks,
            boolean removeAfter
    ) {
        showToast(plugin, player, keyPath, title, icon, frame, revokeDelayTicks, removeAfter, null);
    }

    /**
     * Ephemeral (auto key), with optional sound.
     */
    public static void showToast(
            Plugin plugin,
            Player player,
            String title,
            Material icon,
            Frame frame,
            long revokeDelayTicks,
            SoundProfile soundProfile
    ) {
        String keyPath = "runtime/" + player.getUniqueId() + "_" + System.nanoTime();
        showToast(plugin, player, keyPath, title, icon, frame, revokeDelayTicks, true, soundProfile);
    }

    /**
     * Ephemeral, no sound.
     */
    public static void showToast(
            Plugin plugin,
            Player player,
            String title,
            Material icon,
            Frame frame,
            long revokeDelayTicks
    ) {
        showToast(plugin, player, title, icon, frame, revokeDelayTicks, null);
    }

    /* ---------------- utils ---------------- */

    private static String sanitizePath(String path) {
        return path == null ? "runtime/" + UUID.randomUUID()
                : path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }
}
