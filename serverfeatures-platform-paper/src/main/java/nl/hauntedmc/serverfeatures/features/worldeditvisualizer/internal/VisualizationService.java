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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Coordinates player state, WorldEdit selection reads and packet-only rendering. */
public final class VisualizationService {

    private static final String USE_PERMISSION = "serverfeatures.feature.worldeditvisualizer.use";

    private final WorldEditVisualizer feature;
    private final PacketVisualizationRenderer renderer;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SelectionSnapshot> lastSelections = new ConcurrentHashMap<>();
    private final Map<UUID, PacketVisualHandle> shown = new ConcurrentHashMap<>();
    private final Map<UUID, RenderFailure> failures = new ConcurrentHashMap<>();
    private long pollSequence;

    public VisualizationService(WorldEditVisualizer feature) {
        this.feature = feature;
        this.renderer = new PacketVisualizationRenderer(feature);
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        UUID playerId = player.getUniqueId();
        if (enabled.contains(playerId) || shown.containsKey(playerId)) {
            disable(player);
            return false;
        }
        enable(player, true);
        return true;
    }

    public boolean enable(Player player) {
        return enable(player, false);
    }

    public boolean disable(Player player) {
        boolean changed = enabled.remove(player.getUniqueId());
        clear(player);
        return changed;
    }

    public void invalidate(Player player) {
        clear(player);
    }

    public void handleQuit(Player player) {
        UUID playerId = player.getUniqueId();
        enabled.remove(playerId);
        lastSelections.remove(playerId);
        failures.remove(playerId);
        PacketVisualHandle handle = shown.remove(playerId);
        if (handle != null) {
            handle.discard();
        }
    }

    public void pollSelections() {
        pollSequence++;
        for (UUID playerId : new ArrayList<>(enabled)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                enabled.remove(playerId);
                lastSelections.remove(playerId);
                failures.remove(playerId);
                PacketVisualHandle handle = shown.remove(playerId);
                if (handle != null) {
                    handle.discard();
                }
                continue;
            }
            if (!player.hasPermission(USE_PERMISSION)) {
                disable(player);
                continue;
            }
            renderSelection(player, false);
        }
    }

    public void clear(Player player) {
        destroyVisual(player, true, "state cleanup");
    }

    public void shutdown() {
        for (Map.Entry<UUID, PacketVisualHandle> entry : new ArrayList<>(shown.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                safeClear(entry.getKey(), player, entry.getValue(), "feature shutdown");
            } else {
                entry.getValue().discard();
            }
        }
        enabled.clear();
        lastSelections.clear();
        shown.clear();
        failures.clear();
    }

    private boolean enable(Player player, boolean feedback) {
        UUID playerId = player.getUniqueId();
        clear(player);
        boolean changed = enabled.add(playerId);
        renderSelection(player, feedback);
        return changed;
    }

    private void renderSelection(Player player, boolean feedback) {
        SelectionRead read = readSelection(player);
        if (read.status() != SelectionStatus.VALID) {
            clear(player);
            if (feedback) {
                send(player, read.status() == SelectionStatus.NOT_CUBOID
                        ? "worldeditvisualizer.not_cuboid"
                        : "worldeditvisualizer.no_selection");
            }
            return;
        }

        SelectionSnapshot snapshot = read.snapshot();
        UUID playerId = player.getUniqueId();
        if (snapshot.equals(lastSelections.get(playerId)) && shown.containsKey(playerId)) {
            return;
        }

        RenderFailure previousFailure = failures.get(playerId);
        if (!feedback
                && previousFailure != null
                && previousFailure.snapshot().equals(snapshot)
                && pollSequence < previousFailure.retryAtPoll()) {
            return;
        }

        destroyVisual(player, false, "selection replacement");
        try {
            PacketVisualHandle handle = renderer.render(
                    player,
                    snapshot.minimum(),
                    snapshot.maximum(),
                    snapshot.pos1(),
                    snapshot.pos2()
            );
            shown.put(playerId, handle);
            lastSelections.put(playerId, snapshot);
            failures.remove(playerId);
        } catch (RuntimeException exception) {
            failures.put(playerId, new RenderFailure(snapshot, pollSequence + renderRetryPolls()));
            feature.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Failed to render packet-only WorldEdit selection for "
                            + player.getName() + " (" + playerId + ")",
                    exception
            );
            if (feedback) {
                send(player, "worldeditvisualizer.render_failed");
            }
        }
    }

    private void destroyVisual(Player player, boolean clearFailure, String reason) {
        UUID playerId = player.getUniqueId();
        lastSelections.remove(playerId);
        if (clearFailure) {
            failures.remove(playerId);
        }
        PacketVisualHandle handle = shown.remove(playerId);
        if (handle != null) {
            safeClear(playerId, player, handle, reason);
        }
    }

    private void safeClear(UUID playerId, Player player, PacketVisualHandle handle, String reason) {
        try {
            handle.clear(player);
        } catch (RuntimeException exception) {
            feature.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Failed to destroy packet-only WorldEdit visualization for "
                            + player.getName() + " (" + playerId + ") during " + reason,
                    exception
            );
        }
    }

    private SelectionRead readSelection(Player player) {
        var session = WorldEdit.getInstance().getSessionManager()
                .getIfPresent(BukkitAdapter.adapt(player));
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
            return SelectionRead.notCuboid();
        }
        return SelectionRead.valid(new SelectionSnapshot(
                player.getWorld().getUID(),
                cuboid.getMinimumPoint(),
                cuboid.getMaximumPoint(),
                cuboid.getPos1(),
                cuboid.getPos2()
        ));
    }

    private int renderRetryPolls() {
        int pollTicks = Math.max(1, feature.getInt("poll.interval_ticks", 10));
        int retryTicks = Math.max(pollTicks, feature.getInt("render.retry_interval_ticks", 200));
        return Math.max(1, (int) Math.ceil((double) retryTicks / pollTicks));
    }

    private void send(Player player, String key) {
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
    }

    private enum SelectionStatus {
        VALID,
        NO_SELECTION,
        NOT_CUBOID
    }

    private record SelectionRead(SelectionStatus status, SelectionSnapshot snapshot) {
        private static SelectionRead valid(SelectionSnapshot snapshot) {
            return new SelectionRead(SelectionStatus.VALID, snapshot);
        }

        private static SelectionRead noSelection() {
            return new SelectionRead(SelectionStatus.NO_SELECTION, null);
        }

        private static SelectionRead notCuboid() {
            return new SelectionRead(SelectionStatus.NOT_CUBOID, null);
        }
    }

    private record SelectionSnapshot(
            UUID worldId,
            BlockVector3 minimum,
            BlockVector3 maximum,
            BlockVector3 pos1,
            BlockVector3 pos2
    ) { }

    private record RenderFailure(SelectionSnapshot snapshot, long retryAtPoll) { }
}
