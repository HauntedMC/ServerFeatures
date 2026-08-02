package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/** Player-scoped WorldEdit cuboid rendering using fake block-change packets only. */
public final class VisualizationService {

    private static final String USE_PERMISSION = "serverfeatures.feature.worldeditvisualizer.use";
    private static final int MIN_RENDER_BUDGET = 8;

    private final WorldEditVisualizer feature;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerVisualization> visualizations = new ConcurrentHashMap<>();
    private long pollSequence;

    public VisualizationService(WorldEditVisualizer feature) {
        this.feature = feature;
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public boolean enable(Player player) {
        boolean changed = enabled.add(player.getUniqueId());
        clear(player);
        renderSelection(player, true);
        return changed;
    }

    public boolean disable(Player player) {
        boolean changed = enabled.remove(player.getUniqueId());
        clear(player);
        return changed;
    }

    public void handleQuit(Player player) {
        enabled.remove(player.getUniqueId());
        visualizations.remove(player.getUniqueId());
    }

    public void pollSelections() {
        pollSequence++;
        for (UUID playerId : new ArrayList<>(enabled)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                enabled.remove(playerId);
                visualizations.remove(playerId);
            } else if (!player.hasPermission(USE_PERMISSION)) {
                disable(player);
            } else {
                renderSelection(player, false);
            }
        }
    }

    public void clear(Player player) {
        PlayerVisualization previous = visualizations.remove(player.getUniqueId());
        if (previous != null && previous.worldId().equals(player.getWorld().getUID())) {
            restore(player, previous.renderedPositions());
        }
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(enabled)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clear(player);
            }
        }
        enabled.clear();
        visualizations.clear();
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
        PlayerVisualization previous = visualizations.get(player.getUniqueId());
        boolean unchanged = previous != null && previous.snapshot().equals(snapshot);
        boolean resendDue = previous == null
                || pollSequence - previous.lastSentPoll() >= resendPolls();
        if (unchanged && !resendDue) {
            return;
        }
        if (previous != null) {
            restore(player, previous.renderedPositions());
        }

        int step = Math.max(1, feature.getInt("edge.step_blocks", 1));
        int budget = Math.max(MIN_RENDER_BUDGET, feature.getInt("render.max_blocks", 2048));
        int maxDistance = Math.max(16, feature.getInt("render.max_distance_blocks", 192));
        Set<BlockPosition> positions = CuboidWireframe.sample(
                snapshot.minimum(), snapshot.maximum(), step, budget
        );
        Set<BlockPosition> sent = send(player, classify(positions, snapshot, palette()), maxDistance);
        visualizations.put(player.getUniqueId(), new PlayerVisualization(
                snapshot, player.getWorld().getUID(), sent, pollSequence
        ));
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
                player.getWorld().getUID(), cuboid.getMinimumPoint(), cuboid.getMaximumPoint(),
                cuboid.getPos1(), cuboid.getPos2()
        ));
    }

    private Map<BlockPosition, BlockData> classify(
            Set<BlockPosition> positions, SelectionSnapshot snapshot, RenderPalette palette
    ) {
        Map<BlockPosition, BlockData> result = new LinkedHashMap<>();
        for (BlockPosition position : positions) {
            BlockData data;
            if (position.matches(snapshot.pos1())) {
                data = palette.pos1();
            } else if (position.matches(snapshot.pos2())) {
                data = palette.pos2();
            } else if (position.isCorner(snapshot.minimum(), snapshot.maximum())) {
                data = palette.corner();
            } else {
                data = palette.edge();
            }
            result.put(position, data);
        }
        return result;
    }

    private Set<BlockPosition> send(Player player, Map<BlockPosition, BlockData> blocks, int maxDistance) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        long maxDistanceSquared = (long) maxDistance * maxDistance;
        Set<BlockPosition> sent = new LinkedHashSet<>();
        for (Map.Entry<BlockPosition, BlockData> entry : blocks.entrySet()) {
            BlockPosition position = entry.getKey();
            long dx = (long) position.x() - origin.getBlockX();
            long dy = (long) position.y() - origin.getBlockY();
            long dz = (long) position.z() - origin.getBlockZ();
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared
                    || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                continue;
            }
            player.sendBlockChange(position.location(world), entry.getValue());
            sent.add(position);
        }
        return Set.copyOf(sent);
    }

    private void restore(Player player, Set<BlockPosition> positions) {
        World world = player.getWorld();
        for (BlockPosition position : positions) {
            if (world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                player.sendBlockChange(position.location(world),
                        world.getBlockAt(position.x(), position.y(), position.z()).getBlockData());
            }
        }
    }

    private RenderPalette palette() {
        return new RenderPalette(
                blockData("edge.material", Material.WHITE_STAINED_GLASS),
                blockData("corner.material", Material.LIME_STAINED_GLASS),
                blockData("corner.pos1_material", Material.BLUE_STAINED_GLASS),
                blockData("corner.pos2_material", Material.RED_STAINED_GLASS)
        );
    }

    private BlockData blockData(String key, Material fallback) {
        Material material = Material.matchMaterial(feature.getString(key, fallback.name()));
        if (material == null || !material.isBlock()) {
            material = fallback;
        }
        return material.createBlockData();
    }

    private int resendPolls() {
        int pollTicks = Math.max(1, feature.getInt("poll.interval_ticks", 10));
        int resendTicks = Math.max(pollTicks, feature.getInt("render.resend_interval_ticks", 100));
        return Math.max(1, (int) Math.ceil((double) resendTicks / pollTicks));
    }

    private void send(Player player, String key) {
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
    }

    private enum SelectionStatus { VALID, NO_SELECTION, NOT_CUBOID }

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

    private record SelectionSnapshot(UUID worldId, BlockVector3 minimum, BlockVector3 maximum,
                                     BlockVector3 pos1, BlockVector3 pos2) { }

    private record PlayerVisualization(SelectionSnapshot snapshot, UUID worldId,
                                       Set<BlockPosition> renderedPositions, long lastSentPoll) { }

    private record RenderPalette(BlockData edge, BlockData corner, BlockData pos1, BlockData pos2) { }

    static record BlockPosition(int x, int y, int z) {
        private boolean matches(BlockVector3 vector) {
            return x == vector.x() && y == vector.y() && z == vector.z();
        }
        private boolean isCorner(BlockVector3 minimum, BlockVector3 maximum) {
            return (x == minimum.x() || x == maximum.x())
                    && (y == minimum.y() || y == maximum.y())
                    && (z == minimum.z() || z == maximum.z());
        }
        private Location location(World world) {
            return new Location(world, x, y, z);
        }
    }

    static final class CuboidWireframe {
        private CuboidWireframe() { }

        static Set<BlockPosition> sample(BlockVector3 minimum, BlockVector3 maximum,
                                         int requestedStep, int budget) {
            int step = Math.max(1, requestedStep);
            int safeBudget = Math.max(MIN_RENDER_BUDGET, budget);
            while (true) {
                Set<BlockPosition> points = generate(minimum, maximum, step);
                if (points.size() <= safeBudget || step == Integer.MAX_VALUE) {
                    return trim(points, safeBudget);
                }
                long next = Math.max((long) step + 1L,
                        (long) Math.ceil(step * ((double) points.size() / safeBudget)));
                step = (int) Math.min(Integer.MAX_VALUE, next);
            }
        }

        private static Set<BlockPosition> generate(BlockVector3 minimum, BlockVector3 maximum, int step) {
            LinkedHashSet<BlockPosition> points = new LinkedHashSet<>();
            int minX = minimum.x(), minY = minimum.y(), minZ = minimum.z();
            int maxX = maximum.x(), maxY = maximum.y(), maxZ = maximum.z();
            addAxis(points, minX, maxX, step, x -> new BlockPosition(x, minY, minZ));
            addAxis(points, minX, maxX, step, x -> new BlockPosition(x, minY, maxZ));
            addAxis(points, minX, maxX, step, x -> new BlockPosition(x, maxY, minZ));
            addAxis(points, minX, maxX, step, x -> new BlockPosition(x, maxY, maxZ));
            addAxis(points, minY, maxY, step, y -> new BlockPosition(minX, y, minZ));
            addAxis(points, minY, maxY, step, y -> new BlockPosition(minX, y, maxZ));
            addAxis(points, minY, maxY, step, y -> new BlockPosition(maxX, y, minZ));
            addAxis(points, minY, maxY, step, y -> new BlockPosition(maxX, y, maxZ));
            addAxis(points, minZ, maxZ, step, z -> new BlockPosition(minX, minY, z));
            addAxis(points, minZ, maxZ, step, z -> new BlockPosition(minX, maxY, z));
            addAxis(points, minZ, maxZ, step, z -> new BlockPosition(maxX, minY, z));
            addAxis(points, minZ, maxZ, step, z -> new BlockPosition(maxX, maxY, z));
            return points;
        }

        private static void addAxis(Set<BlockPosition> points, int minimum, int maximum, int step,
                                    IntFunction<BlockPosition> mapper) {
            points.add(mapper.apply(minimum));
            if (minimum == maximum) {
                return;
            }
            for (long value = (long) minimum + step; value < maximum; value += step) {
                points.add(mapper.apply((int) value));
            }
            points.add(mapper.apply(maximum));
        }

        private static Set<BlockPosition> trim(Set<BlockPosition> points, int budget) {
            if (points.size() <= budget) {
                return Set.copyOf(points);
            }
            LinkedHashSet<BlockPosition> trimmed = new LinkedHashSet<>(budget);
            double interval = (double) points.size() / budget;
            double next = 0.0d;
            int index = 0;
            for (BlockPosition point : points) {
                if (index++ >= Math.floor(next) && trimmed.size() < budget) {
                    trimmed.add(point);
                    next += interval;
                }
            }
            return Set.copyOf(trimmed);
        }
    }
}
