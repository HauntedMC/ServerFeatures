package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import io.papermc.paper.math.Position;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends fake block changes to one player. It never creates entities or mutates world blocks.
 */
final class PacketCuboidRenderer {

    private static final int MAX_RENDER_DISTANCE = 512;
    private static final int MAX_RENDER_BLOCKS = 8192;

    private final WorldEditVisualizer feature;

    PacketCuboidRenderer(WorldEditVisualizer feature) {
        this.feature = feature;
    }

    RenderState render(Player player, CuboidSelection selection, RenderState previous) {
        World world = player.getWorld();
        if (!world.getUID().equals(selection.worldId())) {
            clear(player, previous);
            return RenderState.empty(world.getUID());
        }

        int maxDistance = clamp(
                feature.getInt("render.max_distance_blocks", 128), 1, MAX_RENDER_DISTANCE);
        int maxBlocks = clamp(
                feature.getInt("render.max_blocks", 2048), 16, MAX_RENDER_BLOCKS);
        int edgeStep = Math.max(1, feature.getInt("edge.step_blocks", 1));
        BlockPoint viewer = new BlockPoint(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ()
        );

        Material edge = blockMaterial("edge.material", Material.WHITE_STAINED_GLASS);
        Material corner = blockMaterial("corner.material", Material.LIME_STAINED_GLASS);
        Material pos1 = blockMaterial("corner.pos1_material", Material.BLUE_STAINED_GLASS);
        Material pos2 = blockMaterial("corner.pos2_material", Material.RED_STAINED_GLASS);

        Map<BlockPoint, Material> desired = new LinkedHashMap<>();
        addSpecialPoints(desired, selection, viewer, maxDistance, maxBlocks, corner, pos1, pos2);

        int edgeBudget = maxBlocks - desired.size();
        if (edgeBudget > 0) {
            for (BlockPoint point : CuboidOutlineSampler.sample(
                    selection.bounds(), viewer, maxDistance, edgeStep, edgeBudget)) {
                if (desired.size() >= maxBlocks) {
                    break;
                }
                if (isSendable(world, point)) {
                    desired.putIfAbsent(point, edge);
                }
            }
        }

        Map<Position, BlockData> changes = new LinkedHashMap<>();
        restoreRemovedBlocks(world, previous, desired, changes);
        for (Map.Entry<BlockPoint, Material> entry : desired.entrySet()) {
            changes.put(toPosition(entry.getKey()), entry.getValue().createBlockData());
        }
        send(player, changes);

        return new RenderState(world.getUID(), Map.copyOf(desired));
    }

    void clear(Player player, RenderState state) {
        if (state == null || state.blocks().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(state.worldId());
        if (world == null || !player.getWorld().getUID().equals(state.worldId())) {
            return;
        }

        Map<Position, BlockData> changes = new LinkedHashMap<>();
        for (BlockPoint point : state.blocks().keySet()) {
            addRestoration(world, point, changes);
        }
        send(player, changes);
    }

    private void addSpecialPoints(
            Map<BlockPoint, Material> desired,
            CuboidSelection selection,
            BlockPoint viewer,
            int maxDistance,
            int maxBlocks,
            Material corner,
            Material pos1,
            Material pos2
    ) {
        World world = Bukkit.getWorld(selection.worldId());
        if (world == null) {
            return;
        }
        for (BlockPoint point : selection.bounds().corners()) {
            addIfVisible(desired, world, point, viewer, maxDistance, maxBlocks, corner);
        }
        addIfVisible(desired, world, selection.pos1(), viewer, maxDistance, maxBlocks, pos1);
        addIfVisible(desired, world, selection.pos2(), viewer, maxDistance, maxBlocks, pos2);
    }

    private static void addIfVisible(
            Map<BlockPoint, Material> desired,
            World world,
            BlockPoint point,
            BlockPoint viewer,
            int maxDistance,
            int maxBlocks,
            Material material
    ) {
        if (desired.size() >= maxBlocks && !desired.containsKey(point)) {
            return;
        }
        if (CuboidOutlineSampler.isVisible(point, viewer, maxDistance) && isSendable(world, point)) {
            desired.put(point, material);
        }
    }

    private static void restoreRemovedBlocks(
            World world,
            RenderState previous,
            Map<BlockPoint, Material> desired,
            Map<Position, BlockData> changes
    ) {
        if (previous == null || !previous.worldId().equals(world.getUID())) {
            return;
        }
        for (BlockPoint point : previous.blocks().keySet()) {
            if (!desired.containsKey(point)) {
                addRestoration(world, point, changes);
            }
        }
    }

    private static void addRestoration(World world, BlockPoint point, Map<Position, BlockData> changes) {
        if (!isSendable(world, point)) {
            return;
        }
        Position position = toPosition(point);
        changes.put(position, world.getBlockAt(point.x(), point.y(), point.z()).getBlockData());
    }

    private static boolean isSendable(World world, BlockPoint point) {
        return point.y() >= world.getMinHeight()
                && point.y() < world.getMaxHeight()
                && world.isChunkLoaded(point.x() >> 4, point.z() >> 4);
    }

    private Material blockMaterial(String key, Material fallback) {
        Material material = Material.matchMaterial(feature.getString(key, fallback.name()));
        return material != null && material.isBlock() ? material : fallback;
    }

    private static Position toPosition(BlockPoint point) {
        return Position.block(point.x(), point.y(), point.z());
    }

    private static void send(Player player, Map<Position, BlockData> changes) {
        if (!changes.isEmpty() && player.isOnline()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

record CuboidSelection(
        UUID worldId,
        CuboidBounds bounds,
        BlockPoint pos1,
        BlockPoint pos2
) {

    CuboidSelection {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(pos1, "pos1");
        Objects.requireNonNull(pos2, "pos2");
    }
}

record RenderState(UUID worldId, Map<BlockPoint, Material> blocks) {

    RenderState {
        Objects.requireNonNull(worldId, "worldId");
        blocks = Map.copyOf(blocks);
    }

    static RenderState empty(UUID worldId) {
        return new RenderState(worldId, Map.of());
    }
}
