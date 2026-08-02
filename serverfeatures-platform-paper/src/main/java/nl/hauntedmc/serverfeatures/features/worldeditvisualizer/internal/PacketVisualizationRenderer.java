package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.sk89q.worldedit.math.BlockVector3;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates display entity metadata without adding an entity to a Bukkit world, then sends only the
 * corresponding spawn and metadata packets to the requesting player.
 */
final class PacketVisualizationRenderer {

    static final int MAX_ENTITY_COUNT = 24;

    private final WorldEditVisualizer feature;

    PacketVisualizationRenderer(WorldEditVisualizer feature) {
        this.feature = feature;
    }

    PacketVisualHandle render(
            Player viewer,
            BlockVector3 minimum,
            BlockVector3 maximum,
            BlockVector3 pos1,
            BlockVector3 pos2
    ) {
        RenderSettings settings = settings();
        CuboidWireframe wireframe = CuboidWireframe.fromInclusive(
                minimum.x(), minimum.y(), minimum.z(),
                maximum.x(), maximum.y(), maximum.z()
        );
        List<Integer> entityIds = new ArrayList<>(MAX_ENTITY_COUNT);
        try {
            for (CuboidWireframe.Edge edge : wireframe.edges()) {
                spawnEdge(viewer, edge, settings, entityIds);
            }
            for (CuboidWireframe.Point corner : wireframe.corners()) {
                spawnCube(viewer, corner, settings.cornerScale(), settings.cornerBlock(),
                        settings.cornerGlow(), settings.viewRange(), entityIds);
            }

            CuboidWireframe.Point first = blockCenter(pos1);
            CuboidWireframe.Point second = blockCenter(pos2);
            boolean samePoint = pos1.equals(pos2);
            spawnCube(viewer, first, settings.cornerScale(), settings.pos1Block(),
                    settings.pos1Glow(), settings.viewRange(), entityIds);
            if (!samePoint) {
                spawnCube(viewer, second, settings.cornerScale(), settings.pos2Block(),
                        settings.pos2Glow(), settings.viewRange(), entityIds);
            }

            if (settings.labelsEnabled()) {
                if (samePoint) {
                    spawnLabel(viewer, first, settings.labelYOffset(),
                            settings.hashLabels() ? "#1 / #2" : "pos1 / pos2",
                            settings.pos1Glow(), settings.labelScale(), settings.viewRange(), entityIds);
                } else {
                    spawnLabel(viewer, first, settings.labelYOffset(),
                            settings.hashLabels() ? "#1" : "pos1",
                            settings.pos1Glow(), settings.labelScale(), settings.viewRange(), entityIds);
                    spawnLabel(viewer, second, settings.labelYOffset(),
                            settings.hashLabels() ? "#2" : "pos2",
                            settings.pos2Glow(), settings.labelScale(), settings.viewRange(), entityIds);
                }
            }

            int[] ids = entityIds.stream().mapToInt(Integer::intValue).toArray();
            return new PacketVisualHandle(viewer.getWorld().getUID(), ids);
        } catch (RuntimeException exception) {
            int[] ids = entityIds.stream().mapToInt(Integer::intValue).toArray();
            new PacketVisualHandle(viewer.getWorld().getUID(), ids).clear(viewer);
            throw exception;
        }
    }

    private void spawnEdge(
            Player viewer,
            CuboidWireframe.Edge edge,
            RenderSettings settings,
            List<Integer> entityIds
    ) {
        float thickness = settings.edgeScale();
        float half = thickness / 2.0f;
        float length = finiteFloat(edge.length());
        Vector3f translation;
        Vector3f scale;
        switch (edge.axis()) {
            case X -> {
                translation = new Vector3f(0.0f, -half, -half);
                scale = new Vector3f(length, thickness, thickness);
            }
            case Y -> {
                translation = new Vector3f(-half, 0.0f, -half);
                scale = new Vector3f(thickness, length, thickness);
            }
            case Z -> {
                translation = new Vector3f(-half, -half, 0.0f);
                scale = new Vector3f(thickness, thickness, length);
            }
            default -> throw new IllegalStateException("Unhandled wireframe axis: " + edge.axis());
        }
        spawnBlockDisplay(
                viewer,
                edge.origin(),
                settings.edgeBlock(),
                settings.edgeGlow(),
                settings.viewRange(),
                new Transformation(translation, new Quaternionf(), scale, new Quaternionf()),
                entityIds
        );
    }

    private void spawnCube(
            Player viewer,
            CuboidWireframe.Point point,
            float size,
            BlockData blockData,
            Color glow,
            float viewRange,
            List<Integer> entityIds
    ) {
        float half = size / 2.0f;
        spawnBlockDisplay(
                viewer,
                point,
                blockData,
                glow,
                viewRange,
                new Transformation(
                        new Vector3f(-half, -half, -half),
                        new Quaternionf(),
                        new Vector3f(size, size, size),
                        new Quaternionf()
                ),
                entityIds
        );
    }

    private void spawnBlockDisplay(
            Player viewer,
            CuboidWireframe.Point point,
            BlockData blockData,
            Color glow,
            float viewRange,
            Transformation transformation,
            List<Integer> entityIds
    ) {
        World world = viewer.getWorld();
        Location location = new Location(world, point.x(), point.y(), point.z());
        BlockDisplay display = world.createEntity(location, BlockDisplay.class);
        configureDisplay(display, glow, viewRange);
        display.setBlock(blockData);
        display.setTransformation(transformation);
        send(viewer, display, EntityTypes.BLOCK_DISPLAY, entityIds);
    }

    private void spawnLabel(
            Player viewer,
            CuboidWireframe.Point point,
            double yOffset,
            String text,
            Color glow,
            float labelScale,
            float viewRange,
            List<Integer> entityIds
    ) {
        World world = viewer.getWorld();
        Location location = new Location(world, point.x(), point.y() + yOffset, point.z());
        TextDisplay display = world.createEntity(location, TextDisplay.class);
        configureDisplay(display, glow, viewRange);
        display.text(Component.text(text, namedColor(glow)));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setLineWidth(120);
        display.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(labelScale, labelScale, labelScale),
                new Quaternionf()
        ));
        send(viewer, display, EntityTypes.TEXT_DISPLAY, entityIds);
    }

    private void configureDisplay(Display display, Color glow, float viewRange) {
        display.setGravity(false);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setGlowing(true);
        display.setGlowColorOverride(glow);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setShadowRadius(0.0f);
        display.setViewRange(viewRange);
    }

    private void send(
            Player viewer,
            Display display,
            EntityType entityType,
            List<Integer> entityIds
    ) {
        int entityId = display.getEntityId();
        entityIds.add(entityId);
        com.github.retrooper.packetevents.protocol.world.Location location =
                SpigotConversionUtil.fromBukkitLocation(display.getLocation());
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerSpawnEntity(
                        entityId,
                        display.getUniqueId(),
                        entityType,
                        location,
                        location.getYaw(),
                        0,
                        null
                )
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityMetadata(
                        entityId,
                        List.copyOf(SpigotConversionUtil.getEntityMetadata(display))
                )
        );
    }

    private RenderSettings settings() {
        return new RenderSettings(
                blockData("edge.material", Material.WHITE_STAINED_GLASS),
                blockData("corner.material", Material.LIME_STAINED_GLASS),
                blockData("corner.pos1_material", Material.BLUE_STAINED_GLASS),
                blockData("corner.pos2_material", Material.RED_STAINED_GLASS),
                color("glow.edge_color", NamedTextColor.AQUA),
                color("glow.corner_color", NamedTextColor.AQUA),
                color("glow.pos1_color", NamedTextColor.BLUE),
                color("glow.pos2_color", NamedTextColor.RED),
                clampFloat(feature.getDouble("edge.scale", 0.12d), 0.02f, 1.0f),
                clampFloat(feature.getDouble("corner.scale", 0.35d), 0.05f, 2.0f),
                feature.getBoolean("label.enabled", true),
                feature.getDouble("label.y_offset", 0.7d),
                clampFloat(feature.getDouble("label.scale", 1.0d), 0.1f, 4.0f),
                feature.getBoolean("label.show_prefix_hash", false),
                clampFloat(feature.getDouble("render.view_range", 4.0d), 0.1f, 64.0f)
        );
    }

    private BlockData blockData(String key, Material fallback) {
        Material material = Material.matchMaterial(feature.getString(key, fallback.name()));
        if (material == null || !material.isBlock()) {
            material = fallback;
        }
        return material.createBlockData();
    }

    private Color color(String key, NamedTextColor fallback) {
        String configured = feature.getString(key, fallback.toString());
        NamedTextColor parsed = NamedTextColor.NAMES.value(configured.toLowerCase(Locale.ROOT));
        NamedTextColor result = parsed == null ? fallback : parsed;
        int rgb = result.value();
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static NamedTextColor namedColor(Color color) {
        return NamedTextColor.nearestTo(net.kyori.adventure.text.format.TextColor.color(color.asRGB()));
    }

    private static CuboidWireframe.Point blockCenter(BlockVector3 point) {
        return new CuboidWireframe.Point(
                point.x() + 0.5d,
                point.y() + 0.5d,
                point.z() + 0.5d
        );
    }

    private static float finiteFloat(double value) {
        if (!Double.isFinite(value) || value > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Display scale is not representable as a finite float");
        }
        return (float) value;
    }

    private static float clampFloat(double value, float minimum, float maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return (float) Math.max(minimum, Math.min(maximum, value));
    }

    private record RenderSettings(
            BlockData edgeBlock,
            BlockData cornerBlock,
            BlockData pos1Block,
            BlockData pos2Block,
            Color edgeGlow,
            Color cornerGlow,
            Color pos1Glow,
            Color pos2Glow,
            float edgeScale,
            float cornerScale,
            boolean labelsEnabled,
            double labelYOffset,
            float labelScale,
            boolean hashLabels,
            float viewRange
    ) { }
}
