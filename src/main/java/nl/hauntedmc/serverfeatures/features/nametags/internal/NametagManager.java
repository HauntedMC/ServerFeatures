package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Minimal manager: spawns/remounts TextDisplays, keeps them mounted,
 * and exposes text refresh for hooks. No packets, no per-viewer diffs.
 */
public class NametagManager {

    private final Nametags feature;
    private final NametagRegistry registry = new NametagRegistry();

    // Config snapshot (read once)
    private final boolean enabledOnStartup;
    private final int updateIntervalTicks;
    private final double spawnYOffsetBlocks;
    private final double translationY;
    private final int lineWidth;
    private final boolean shadow;
    private final boolean seeThrough;
    private final boolean useDefaultBg;
    private final int backgroundARGB;

    private final FeatureTaskManager tasks;

    public NametagManager(Nametags feature) {
        this.feature = feature;
        this.tasks = feature.getLifecycleManager().getTaskManager();

        // --- Inline config reads (snapshotted once) ---
        this.enabledOnStartup     = getBoolean("enabled", false);
        this.updateIntervalTicks  = getInt("update_interval_ticks", 10);
        this.spawnYOffsetBlocks   = getDouble("y_offset_blocks", 1.80d);
        this.translationY         = getDouble("translation_y", 0.30d);
        this.lineWidth            = getInt("line_width", 200);
        this.shadow               = getBoolean("shadow", true);
        this.seeThrough           = getBoolean("see_through", false);
        this.useDefaultBg         = getBoolean("use_default_bg", false);
        this.backgroundARGB       = getInt("background_argb", 0x00000000);

        // Self-heal loop: keep displays mounted and rebuild if missing
        if (updateIntervalTicks > 0) {
            tasks.scheduleRepeatingTask(this::tickMaintain, BukkitTime.ticks(updateIntervalTicks));
        }
    }

    public void initializeOnlinePlayers() {
        if (!enabledOnStartup) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            respawn(p);
        }
    }

    /** Rebuilds (or creates) the display and (re)mounts it. */
    public void respawn(Player player) {
        if (player == null || !player.isOnline()) return;

        Nametag tag = registry.getNametag(player.getUniqueId()).orElseGet(() -> {
            Nametag nt = new Nametag(player);
            nt.configureFromDefaults(
                    shadow, seeThrough, useDefaultBg, backgroundARGB,
                    lineWidth, translationY, spawnYOffsetBlocks
            );
            registry.register(nt);
            return nt;
        });

        tag.configureFromDefaults(
                shadow, seeThrough, useDefaultBg, backgroundARGB,
                lineWidth, translationY, spawnYOffsetBlocks
        );
        tag.spawnOrRespawn();
    }

    /** Removes the display for a player, if any. */
    public void remove(Player player) {
        Optional<Nametag> opt = registry.getNametag(player.getUniqueId());
        opt.ifPresent(tag -> {
            tag.remove();
            registry.unregister(tag);
        });
    }

    /** Removes all displays (feature disable). */
    public void removeAllNametags() {
        for (Nametag tag : registry.getAllNametags()) {
            tag.remove();
        }
        registry.getAllNametags().clear();
    }

    /** Called by LuckPerms/placeholder hooks to refresh text only. */
    public void refreshText(Player player) {
        Optional<Nametag> opt = registry.getNametag(player.getUniqueId());
        if (opt.isEmpty()) {
            // If feature is enabled and no tag exists, create it—keeps UX consistent
            if (enabledOnStartup && player.isOnline()) respawn(player);
            return;
        }
        opt.get().updateTextOnly();
    }

    /** Periodic maintenance: remount if needed; rebuild if entity went missing. */
    private void tickMaintain() {
        for (Nametag tag : registry.getAllNametags()) {
            Player owner = tag.getNametagOwner();
            if (owner == null || !owner.isOnline()) continue;

            if (tag.getDisplay() == null || tag.getDisplay().isDead() || tag.getDisplay().isValid() == false) {
                // recreate missing display
                tag.spawnOrRespawn();
                continue;
            }

            // make sure it's still mounted
            tag.ensureMounted();
        }
    }

    // --- tiny typed getters (config is Object-typed) ---
    private boolean getBoolean(String key, boolean def) {
        Object v = feature.getConfigHandler().getSetting(key);
        return (v instanceof Boolean b) ? b : def;
    }

    private int getInt(String key, int def) {
        Object v = feature.getConfigHandler().getSetting(key);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Throwable ignored) {}
        return def;
    }

    private double getDouble(String key, double def) {
        Object v = feature.getConfigHandler().getSetting(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Throwable ignored) {}
        return def;
    }
}
