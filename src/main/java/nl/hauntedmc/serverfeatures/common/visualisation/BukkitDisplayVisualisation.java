package nl.hauntedmc.serverfeatures.common.visualisation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bukkit implementation of {@link Visualisation} using Display entities (1.19+).
 * Produces per-player visualisations (viewer-only visibility).
 */
public final class BukkitDisplayVisualisation implements Visualisation {

    private final Plugin plugin;

    public BukkitDisplayVisualisation(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public VisualHandle show(Player viewer, RegionShape shape, VisualOptions options) {
        World world = viewer.getWorld();
        List<Display> spawned = new ArrayList<>();

        // 1) Named points first (for special styling and labels)
        Map<String, Vector> named = shape.namedPoints();
        int index = 0;
        for (Map.Entry<String, Vector> e : named.entrySet()) {
            String key = e.getKey();
            Vector v = e.getValue();
            Material mat = options.materialForNamedPoint(key);
            NamedTextColor glow = options.glowForNamedPoint(key);

            spawned.add(spawnCube(world, v, options.cornerScale(), mat, glow, viewer));
            if (options.labelEnabled()) {
                String text = options.labelTextStrategy() == null ? key :
                        options.labelTextStrategy().labelFor(key, index);
                if (text != null && !text.isEmpty()) {
                    spawned.add(spawnLabel(world, v.clone().add(new Vector(0, options.labelYOffset(), 0)),
                            text, glow, options.labelScale(), viewer));
                }
            }
            index++;
        }

        // 2) Other corners (that are not named points)
        List<Vector> corners = shape.cornerCenters();
        for (Vector c : corners) {
            // skip if coincides with a named point (exact comparison is okay because we compute the same centers)
            boolean isNamed = named.values().stream().anyMatch(v -> v.equals(c));
            if (isNamed) continue;
            spawned.add(spawnCube(world, c, options.cornerScale(), options.cornerMaterial(), options.cornerGlow(), viewer));
        }

        // 3) Edges
        for (Vector p : shape.sampleEdgePoints(options.edgeStepBlocks())) {
            spawned.add(spawnCube(world, p, options.edgeScale(), options.edgeMaterial(), options.edgeGlow(), viewer));
        }

        // Return handle to clear
        return new BukkitDisplaysHandle(spawned);
    }

    /* ==== helpers ==== */

    private Display spawnCube(World w, Vector pos, float scale, Material mat, NamedTextColor glow, Player viewer) {
        BlockDisplay d = w.spawn(new Location(w, pos.getX(), pos.getY(), pos.getZ()), BlockDisplay.class, ent -> {
            ent.setBlock(w.getBlockAt(ent.getLocation()).getBlockData()); // force init before setBlock?
            ent.setBlock(Bukkit.createBlockData(mat));
            ent.setBrightness(new Display.Brightness(15, 15));
            ent.setGlowing(true);
            ent.setGlowColorOverride(toBukkitColor(glow));
            ent.setShadowRadius(0f);
            ent.setVisibleByDefault(false);
            float half = scale / 2f;
            ent.setTransformation(new Transformation(
                    new Vector3f(-half, -half, -half),
                    new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
        });
        viewer.showEntity(plugin, d);
        return d;
    }

    private Display spawnLabel(World w, Vector pos, String text, NamedTextColor glow, float labelScale, Player viewer) {
        TextDisplay t = w.spawn(new Location(w, pos.getX(), pos.getY(), pos.getZ()), TextDisplay.class, ent -> {
            ent.text(Component.text(text, glow == null ? NamedTextColor.WHITE : glow));
            ent.setBillboard(Display.Billboard.CENTER);
            ent.setSeeThrough(true);
            ent.setGlowing(true);
            ent.setGlowColorOverride(toBukkitColor(glow));
            ent.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            ent.setLineWidth(120);
            ent.setShadowed(true);
            ent.setVisibleByDefault(false);
            ent.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(labelScale, labelScale, labelScale),
                    new Quaternionf()
            ));
        });
        viewer.showEntity(plugin, t);
        return t;
    }

    private static Color toBukkitColor(NamedTextColor named) {
        if (named == null) return Color.WHITE;
        int rgb = named.value();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Color.fromRGB(r, g, b);
    }

    private static final class BukkitDisplaysHandle implements VisualHandle {
        private final List<Display> displays;
        private final AtomicBoolean cleared = new AtomicBoolean(false);

        private BukkitDisplaysHandle(List<Display> displays) {
            this.displays = displays;
        }

        @Override
        public void clear() {
            if (cleared.compareAndSet(false, true)) {
                for (Display d : displays) {
                    if (d != null && !d.isDead()) d.remove();
                }
                displays.clear();
            }
        }

        @Override
        public boolean isCleared() {
            return cleared.get();
        }
    }
}
