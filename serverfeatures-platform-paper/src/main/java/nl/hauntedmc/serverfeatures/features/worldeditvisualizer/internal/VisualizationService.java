package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Tracks per-player WorldEdit selections and owns their virtual display state.
 */
public final class VisualizationService {

    public static final String USE_PERMISSION = "serverfeatures.feature.worldeditvisualizer.use";

    private static final int MAX_FULL_REFRESH_INTERVAL_TICKS = 72_000;

    private final WorldEditVisualizer feature;
    private final PacketDisplayRenderer renderer;
    private final Set<UUID> enabled = new HashSet<>();
    private final Map<UUID, RenderState> rendered = new HashMap<>();
    private final Map<UUID, RenderFingerprint> fingerprints = new HashMap<>();
    private final Map<UUID, Long> nextFullRefreshNanos = new HashMap<>();

    public VisualizationService(WorldEditVisualizer feature) {
        this.feature = feature;
        this.renderer = new PacketDisplayRenderer(feature);
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public boolean shouldAutoEnable(Player player) {
        return feature.getBoolean("auto_enable_on_join", true) && player.hasPermission(USE_PERMISSION);
    }

    public ToggleResult toggle(Player player) {
        if (isEnabled(player) || rendered.containsKey(player.getUniqueId())) {
            disable(player, true);
            return new ToggleResult(false, RefreshResult.DISABLED);
        }
        RefreshResult result = enable(player);
        return new ToggleResult(isEnabled(player), result);
    }

    public RefreshResult enable(Player player) {
        UUID uuid = player.getUniqueId();
        enabled.add(uuid);
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        return refreshNow(player);
    }

    public boolean disable(Player player, boolean destroy) {
        UUID uuid = player.getUniqueId();
        boolean changed = enabled.remove(uuid);
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        RenderState state = rendered.remove(uuid);
        if (destroy) {
            destroy(player, state);
        }
        return changed || state != null;
    }

    public RefreshResult refreshNow(Player player) {
        if (!isEnabled(player)) {
            return RefreshResult.DISABLED;
        }
        return updateSafely(player, true);
    }

    public void invalidate(Player player, boolean destroy) {
        UUID uuid = player.getUniqueId();
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        RenderState state = rendered.remove(uuid);
        if (destroy) {
            destroy(player, state);
        }
    }

    public void forget(Player player) {
        forget(player.getUniqueId());
    }

    private void forget(UUID uuid) {
        enabled.remove(uuid);
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        rendered.remove(uuid);
    }

    public void pollSelections() {
        if (enabled.isEmpty()) {
            return;
        }
        for (UUID uuid : new ArrayList<>(enabled)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                forget(uuid);
                continue;
            }
            if (!player.hasPermission(USE_PERMISSION)) {
                disable(player, true);
                continue;
            }
            updateSafely(player, false);
        }
    }

    public void shutdown() {
        for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
            RenderState state = rendered.remove(player.getUniqueId());
            destroy(player, state);
        }
        enabled.clear();
        rendered.clear();
        fingerprints.clear();
        nextFullRefreshNanos.clear();
    }

    private RefreshResult updateSafely(Player player, boolean force) {
        try {
            return updatePlayer(player, force);
        } catch (RuntimeException exception) {
            feature.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Failed to update WorldEdit visualization for " + player.getName(),
                    exception
            );
            disableAfterFailure(player);
            return RefreshResult.FAILED;
        }
    }

    private RefreshResult updatePlayer(Player player, boolean force) {
        SelectionRead selectionRead = readSelection(player);
        if (selectionRead.result() != RefreshResult.RENDERED) {
            clearStale(player);
            return selectionRead.result();
        }

        CuboidSelection selection = selectionRead.selection();
        int movementCell = clamp(feature.getInt("render.movement_refresh_blocks", 8), 1, 32);
        RenderFingerprint fingerprint = new RenderFingerprint(
                selection,
                Math.floorDiv(player.getLocation().getBlockX(), movementCell),
                Math.floorDiv(player.getLocation().getBlockY(), movementCell),
                Math.floorDiv(player.getLocation().getBlockZ(), movementCell)
        );
        UUID uuid = player.getUniqueId();
        long now = System.nanoTime();
        boolean fullRefreshDue = now >= nextFullRefreshNanos.getOrDefault(uuid, Long.MAX_VALUE);
        if (!force && !fullRefreshDue && fingerprint.equals(fingerprints.get(uuid))) {
            return RefreshResult.RENDERED;
        }

        RenderState previous = rendered.get(uuid);
        RenderState current = renderer.render(player, selection, previous, force || fullRefreshDue);
        rendered.put(uuid, current);
        fingerprints.put(uuid, fingerprint);
        scheduleFullRefresh(uuid, now);
        return RefreshResult.RENDERED;
    }

    private SelectionRead readSelection(Player player) {
        var actor = BukkitAdapter.adapt(player);
        var session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
        if (session == null) {
            return SelectionRead.noSelection();
        }

        Region region;
        try {
            region = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (Exception ignored) {
            return SelectionRead.noSelection();
        }
        if (!(region instanceof CuboidRegion cuboid)) {
            return SelectionRead.unsupported();
        }

        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        CuboidSelection selection = new CuboidSelection(
                player.getWorld().getUID(),
                new CuboidBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()),
                point(cuboid.getPos1()),
                point(cuboid.getPos2())
        );
        return SelectionRead.ready(selection);
    }

    private void disableAfterFailure(Player player) {
        UUID uuid = player.getUniqueId();
        enabled.remove(uuid);
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        RenderState state = rendered.remove(uuid);
        destroy(player, state);
    }

    private void clearStale(Player player) {
        UUID uuid = player.getUniqueId();
        fingerprints.remove(uuid);
        nextFullRefreshNanos.remove(uuid);
        RenderState state = rendered.remove(uuid);
        destroy(player, state);
    }

    private void scheduleFullRefresh(UUID uuid, long now) {
        int intervalTicks = clamp(
                feature.getInt("render.full_refresh_interval_ticks", 600),
                0,
                MAX_FULL_REFRESH_INTERVAL_TICKS
        );
        if (intervalTicks == 0) {
            nextFullRefreshNanos.put(uuid, Long.MAX_VALUE);
            return;
        }
        long delayNanos = TimeUnit.MILLISECONDS.toNanos(intervalTicks * 50L);
        nextFullRefreshNanos.put(uuid, saturatingAdd(now, delayNanos));
    }

    private void destroy(Player player, RenderState state) {
        try {
            renderer.clear(player, state);
        } catch (RuntimeException exception) {
            feature.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Failed to destroy WorldEdit visualization for " + player.getName(),
                    exception
            );
        }
    }

    private static BlockPoint point(BlockVector3 vector) {
        return new BlockPoint(vector.x(), vector.y(), vector.z());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long saturatingAdd(long value, long amount) {
        if (amount > 0L && value > Long.MAX_VALUE - amount) {
            return Long.MAX_VALUE;
        }
        return value + amount;
    }

    public enum RefreshResult {
        RENDERED,
        NO_SELECTION,
        UNSUPPORTED_SELECTION,
        DISABLED,
        FAILED;

        public String messageKey() {
            return switch (this) {
                case NO_SELECTION -> "worldeditvisualizer.no_selection";
                case UNSUPPORTED_SELECTION -> "worldeditvisualizer.not_cuboid";
                case RENDERED -> "worldeditvisualizer.refreshed";
                case DISABLED -> "worldeditvisualizer.disabled";
                case FAILED -> "worldeditvisualizer.failed";
            };
        }
    }

    public record ToggleResult(boolean enabled, RefreshResult refreshResult) {
    }

    private record RenderFingerprint(CuboidSelection selection, int cellX, int cellY, int cellZ) {
    }

    private record SelectionRead(RefreshResult result, CuboidSelection selection) {

        static SelectionRead ready(CuboidSelection selection) {
            return new SelectionRead(RefreshResult.RENDERED, selection);
        }

        static SelectionRead noSelection() {
            return new SelectionRead(RefreshResult.NO_SELECTION, null);
        }

        static SelectionRead unsupported() {
            return new SelectionRead(RefreshResult.UNSUPPORTED_SELECTION, null);
        }
    }
}
