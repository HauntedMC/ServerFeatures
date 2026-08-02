package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns virtual display entities that exist only in one player's client.
 * Bukkit entity instances are used solely as unspawned metadata templates.
 */
final class PacketDisplayRenderer {

    private static final int MAX_RENDER_DISTANCE = 512;
    private static final int MAX_RENDER_ENTITIES = 4096;

    private final WorldEditVisualizer feature;
    private Map<VisualKind, List<EntityData<?>>> metadata;

    PacketDisplayRenderer(WorldEditVisualizer feature) {
        this.feature = feature;
    }

    RenderState render(
            Player player,
            CuboidSelection selection,
            RenderState previous,
            boolean force
    ) {
        World world = player.getWorld();
        if (!world.getUID().equals(selection.worldId())) {
            clear(player, previous);
            return RenderState.empty(world.getUID());
        }

        Map<VisualKey, VisualKind> desired = buildDesired(player, selection);
        LinkedHashMap<VisualKey, VirtualEntity> active = new LinkedHashMap<>();
        if (!force && previous != null && previous.worldId().equals(world.getUID())) {
            active.putAll(previous.entities());
        } else {
            clear(player, previous);
        }

        int[] removed = active.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey()))
                .mapToInt(entry -> entry.getValue().entityId())
                .toArray();
        if (removed.length > 0) {
            destroy(player, removed);
            active.entrySet().removeIf(entry -> !desired.containsKey(entry.getKey()));
        }

        Map<VisualKind, List<EntityData<?>>> metadataByKind = metadata(world);
        try {
            for (Map.Entry<VisualKey, VisualKind> entry : desired.entrySet()) {
                if (active.containsKey(entry.getKey())) {
                    continue;
                }
                VirtualEntity entity = spawn(
                        player,
                        entry.getKey(),
                        entry.getValue(),
                        metadataByKind.get(entry.getValue())
                );
                active.put(entry.getKey(), entity);
            }
            return new RenderState(world.getUID(), Map.copyOf(active));
        } catch (RuntimeException exception) {
            try {
                destroy(player, active.values().stream()
                        .mapToInt(VirtualEntity::entityId)
                        .toArray());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    void clear(Player player, RenderState state) {
        if (state == null || state.entities().isEmpty() || !player.isOnline()) {
            return;
        }
        int[] entityIds = state.entities().values().stream()
                .mapToInt(VirtualEntity::entityId)
                .toArray();
        destroy(player, entityIds);
    }

    private Map<VisualKey, VisualKind> buildDesired(Player player, CuboidSelection selection) {
        World world = player.getWorld();
        int maxEntities = clamp(
                feature.getInt("render.max_entities", 1024), 16, MAX_RENDER_ENTITIES);
        double maxDistance = clamp(
                feature.getDouble("render.max_distance_blocks", 128.0), 1.0, MAX_RENDER_DISTANCE);
        double edgeStep = finiteAtLeast(
                feature.getDouble("edge.step_blocks", 0.25), 0.05, 0.25);
        VisualPoint viewer = VisualPoint.of(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
        );

        LinkedHashMap<VisualPoint, VisualKind> blockDisplays = new LinkedHashMap<>();
        for (VisualPoint corner : selection.bounds().corners()) {
            addBlock(blockDisplays, world, corner, viewer, maxDistance, maxEntities, VisualKind.CORNER);
        }
        addBlock(blockDisplays, world, selection.pos1().center(), viewer,
                maxDistance, maxEntities, VisualKind.POS1);
        addBlock(blockDisplays, world, selection.pos2().center(), viewer,
                maxDistance, maxEntities, VisualKind.POS2);

        LinkedHashMap<VisualKey, VisualKind> desired = new LinkedHashMap<>();
        blockDisplays.forEach((point, kind) -> desired.put(new VisualKey(point, kind), kind));

        if (feature.getBoolean("label.enabled", true)) {
            addLabel(desired, world, selection.pos1().center(), viewer,
                    maxDistance, maxEntities, VisualKind.LABEL_POS1);
            addLabel(desired, world, selection.pos2().center(), viewer,
                    maxDistance, maxEntities, VisualKind.LABEL_POS2);
        }

        int edgeBudget = maxEntities - desired.size();
        if (edgeBudget > 0) {
            for (VisualPoint point : CuboidOutlineSampler.sample(
                    selection.bounds(), viewer, maxDistance, edgeStep, edgeBudget)) {
                if (desired.size() >= maxEntities) {
                    break;
                }
                if (!blockDisplays.containsKey(point) && isWithinWorld(world, point)) {
                    desired.put(new VisualKey(point, VisualKind.EDGE), VisualKind.EDGE);
                }
            }
        }
        return Map.copyOf(desired);
    }

    private void addLabel(
            Map<VisualKey, VisualKind> desired,
            World world,
            VisualPoint anchor,
            VisualPoint viewer,
            double maxDistance,
            int maxEntities,
            VisualKind kind
    ) {
        if (desired.size() >= maxEntities
                || !CuboidOutlineSampler.isVisible(anchor, viewer, maxDistance)) {
            return;
        }
        double yOffset = finiteClamped(
                feature.getDouble("label.y_offset", 0.8), -16.0, 16.0, 0.8);
        VisualPoint point = anchor.offset(0.0, yOffset, 0.0);
        if (isWithinWorld(world, point)) {
            desired.put(new VisualKey(point, kind), kind);
        }
    }

    private static void addBlock(
            Map<VisualPoint, VisualKind> desired,
            World world,
            VisualPoint point,
            VisualPoint viewer,
            double maxDistance,
            int maxEntities,
            VisualKind kind
    ) {
        if (desired.size() >= maxEntities && !desired.containsKey(point)) {
            return;
        }
        if (CuboidOutlineSampler.isVisible(point, viewer, maxDistance)
                && isWithinWorld(world, point)) {
            desired.put(point, kind);
        }
    }

    private Map<VisualKind, List<EntityData<?>>> metadata(World world) {
        if (metadata != null) {
            return metadata;
        }
        EnumMap<VisualKind, List<EntityData<?>>> generated = new EnumMap<>(VisualKind.class);
        generated.put(VisualKind.EDGE, blockMetadata(
                world,
                material("edge.material", Material.WHITE_STAINED_GLASS),
                feature.getDouble("edge.scale", 0.15),
                color("glow.edge_color", Color.AQUA)
        ));
        generated.put(VisualKind.CORNER, blockMetadata(
                world,
                material("corner.material", Material.LIME_STAINED_GLASS),
                feature.getDouble("corner.scale", 1.0),
                color("glow.corner_color", Color.LIME)
        ));
        generated.put(VisualKind.POS1, blockMetadata(
                world,
                material("corner.pos1_material", Material.BLUE_STAINED_GLASS),
                feature.getDouble("corner.scale", 1.0),
                color("glow.pos1_color", Color.BLUE)
        ));
        generated.put(VisualKind.POS2, blockMetadata(
                world,
                material("corner.pos2_material", Material.RED_STAINED_GLASS),
                feature.getDouble("corner.scale", 1.0),
                color("glow.pos2_color", Color.RED)
        ));
        generated.put(VisualKind.LABEL_POS1, textMetadata(
                world,
                labelText(1),
                color("glow.pos1_color", Color.BLUE)
        ));
        generated.put(VisualKind.LABEL_POS2, textMetadata(
                world,
                labelText(2),
                color("glow.pos2_color", Color.RED)
        ));
        metadata = Map.copyOf(generated);
        return metadata;
    }

    private List<EntityData<?>> blockMetadata(
            World world,
            Material material,
            double configuredScale,
            Color glowColor
    ) {
        BlockDisplay display = world.createEntity(templateLocation(world), BlockDisplay.class);
        float scale = (float) clamp(configuredScale, 0.01, 8.0);
        float half = scale / 2.0f;
        configureDisplay(display, glowColor, scale);
        display.setBlock(Bukkit.createBlockData(material));
        display.setTransformation(new Transformation(
                new Vector3f(-half, -half, -half),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
        return List.copyOf(SpigotConversionUtil.getEntityMetadata(display));
    }

    private List<EntityData<?>> textMetadata(World world, String text, Color glowColor) {
        TextDisplay display = world.createEntity(templateLocation(world), TextDisplay.class);
        float scale = (float) clamp(feature.getDouble("label.scale", 0.8), 0.1, 4.0);
        configureDisplay(display, glowColor, Math.max(1.0f, scale));
        display.text(Component.text(text, TextColor.color(
                glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue())));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
        display.setLineWidth(120);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
        return List.copyOf(SpigotConversionUtil.getEntityMetadata(display));
    }

    private void configureDisplay(Display display, Color glowColor, float displaySize) {
        double maxDistance = clamp(
                feature.getDouble("render.max_distance_blocks", 128.0), 1.0, MAX_RENDER_DISTANCE);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setGlowing(true);
        display.setGlowColorOverride(glowColor);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
        display.setViewRange((float) Math.max(1.0, (maxDistance / 64.0) + 0.25));
        display.setDisplayWidth(Math.max(1.0f, displaySize * 2.0f));
        display.setDisplayHeight(Math.max(1.0f, displaySize * 2.0f));
    }

    private VirtualEntity spawn(
            Player player,
            VisualKey key,
            VisualKind kind,
            List<EntityData<?>> entityMetadata
    ) {
        int entityId = SpigotReflectionUtil.generateEntityId(player.getWorld());
        UUID playerId = player.getUniqueId();
        long unsignedEntityId = Integer.toUnsignedLong(entityId);
        UUID uuid = new UUID(
                playerId.getMostSignificantBits() ^ (unsignedEntityId << 32),
                playerId.getLeastSignificantBits() ^ unsignedEntityId
        );
        Location location = new Location(
                player.getWorld(), key.point().x(), key.point().y(), key.point().z());
        EntityType entityType = kind.isText() ? EntityTypes.TEXT_DISPLAY : EntityTypes.BLOCK_DISPLAY;

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerSpawnEntity(
                            entityId,
                            uuid,
                            entityType,
                            SpigotConversionUtil.fromBukkitLocation(location),
                            0.0f,
                            0,
                            null
                    ));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityMetadata(entityId, entityMetadata));
            return new VirtualEntity(entityId);
        } catch (RuntimeException exception) {
            try {
                destroy(player, new int[]{entityId});
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static void destroy(Player player, int[] entityIds) {
        if (entityIds.length > 0 && player.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    player, new WrapperPlayServerDestroyEntities(entityIds));
        }
    }

    private Material material(String key, Material fallback) {
        Material material = Material.matchMaterial(feature.getString(key, fallback.name()));
        return material != null && material.isBlock() ? material : fallback;
    }

    private Color color(String key, Color fallback) {
        String configured = feature.getString(key, "").trim().toLowerCase(Locale.ROOT);
        return switch (configured) {
            case "aqua", "cyan" -> Color.AQUA;
            case "black" -> Color.BLACK;
            case "blue" -> Color.BLUE;
            case "fuchsia", "magenta", "light_purple" -> Color.FUCHSIA;
            case "gray", "grey" -> Color.GRAY;
            case "green", "dark_green" -> Color.GREEN;
            case "lime" -> Color.LIME;
            case "maroon", "dark_red" -> Color.MAROON;
            case "navy", "dark_blue" -> Color.NAVY;
            case "olive", "dark_yellow" -> Color.OLIVE;
            case "orange", "gold" -> Color.ORANGE;
            case "purple", "dark_purple" -> Color.PURPLE;
            case "red" -> Color.RED;
            case "silver", "light_gray", "light_grey" -> Color.SILVER;
            case "teal", "dark_aqua" -> Color.TEAL;
            case "white" -> Color.WHITE;
            case "yellow" -> Color.YELLOW;
            default -> parseHex(configured, fallback);
        };
    }

    private static Color parseHex(String configured, Color fallback) {
        String value = configured.startsWith("#") ? configured.substring(1) : configured;
        if (!value.matches("[0-9a-f]{6}")) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String labelText(int index) {
        return feature.getBoolean("label.show_prefix_hash", true)
                ? "#" + index
                : "pos" + index;
    }

    private static Location templateLocation(World world) {
        double y = world.getMinHeight()
                + ((world.getMaxHeight() - world.getMinHeight()) / 2.0);
        return new Location(world, 0.0, y, 0.0);
    }

    private static boolean isWithinWorld(World world, VisualPoint point) {
        return point.y() >= world.getMinHeight() - 2.0
                && point.y() <= world.getMaxHeight() + 4.0;
    }

    private static double finiteAtLeast(double value, double minimum, double fallback) {
        return Double.isFinite(value) ? Math.max(minimum, value) : fallback;
    }

    private static double finiteClamped(
            double value,
            double minimum,
            double maximum,
            double fallback
    ) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
