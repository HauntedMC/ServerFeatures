package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VisualizationService {

    private final WorldEditVisualizer feature;

    // per-player enabled state and last snapshot
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SelectionSnapshot> last = new ConcurrentHashMap<>();
    private final Map<UUID, List<Display>> shown = new ConcurrentHashMap<>();

    // visuals from config (resolved once)
    private final Material edgeMat;
    private final Material cornerMat;
    private final Material pos1CornerMat;
    private final Material pos2CornerMat;
    private final double step;
    private final float edgeScale;
    private final float cornerScale;
    private final NamedTextColor edgeGlow;
    private final NamedTextColor pos1Glow;
    private final NamedTextColor pos2Glow;
    private final boolean labelEnabled;
    private final double labelYOffset;
    private final float labelScale;
    private final boolean labelHash;

    public VisualizationService(WorldEditVisualizer feature) {
        this.feature = feature;

        this.edgeMat = safeMaterial(feature.getString("edge.material", "WHITE_STAINED_GLASS"), Material.WHITE_STAINED_GLASS);
        this.cornerMat = safeMaterial(feature.getString("corner.material", "LIME_STAINED_GLASS"), Material.LIME_STAINED_GLASS);
        this.pos1CornerMat = safeMaterial(feature.getString("corner.pos1_material", "BLUE_STAINED_GLASS"), Material.BLUE_STAINED_GLASS);
        this.pos2CornerMat = safeMaterial(feature.getString("corner.pos2_material", "RED_STAINED_GLASS"), Material.RED_STAINED_GLASS);

        this.edgeGlow = parseNamedColor(feature.getString("glow.edge_color", "aqua"), NamedTextColor.AQUA);
        this.pos1Glow = parseNamedColor(feature.getString("glow.pos1_color", "blue"), NamedTextColor.BLUE);
        this.pos2Glow = parseNamedColor(feature.getString("glow.pos2_color", "red"), NamedTextColor.RED);

        this.step = Math.max(0.25d, feature.getDouble("edge.step_blocks", 0.5d));
        this.edgeScale = clampScale((float) feature.getDouble("edge.scale", 0.18d));
        this.cornerScale = clampScale((float) feature.getDouble("corner.scale", 0.45d));

        this.labelEnabled = feature.getBoolean("label.enabled", true);
        this.labelYOffset = feature.getDouble("label.y_offset", 0.7d);
        this.labelScale = (float) Math.max(0.25d, feature.getDouble("label.scale", 1.0d));
        this.labelHash = feature.getBoolean("label.show_prefix_hash", false);
    }

    /* Public API */

    public boolean toggle(Player p) {
        return enabled.contains(p.getUniqueId()) ? !disable(p, true) : enable(p);
    }

    public boolean enable(Player p) {
        if (enabled.add(p.getUniqueId())) {
            tryShowFromSelection(p, false);
            return true;
        }
        return false;
    }

    public boolean disable(Player p, boolean clearNow) {
        boolean changed = enabled.remove(p.getUniqueId());
        last.remove(p.getUniqueId());
        if (clearNow) clear(p);
        return changed;
    }

    public void clear(Player p) {
        List<Display> list = shown.remove(p.getUniqueId());
        if (list != null) list.forEach(e -> { if (!e.isDead()) e.remove(); });
    }

    public void pollSelections() {
        if (enabled.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(enabled)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                disableOffline(uuid);
                continue;
            }
            tryShowFromSelection(p, false);
        }
    }

    public void tryShowFromSelection(Player p, boolean feedback) {
        var session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(p));
        if (session == null) {
            if (feedback) send(p, "worldeditvisualizer.no_selection");
            return;
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(p.getWorld());
        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (Exception ex) {
            if (feedback) send(p, "worldeditvisualizer.no_selection");
            return;
        }
        if (!(region instanceof CuboidRegion cuboid)) {
            if (feedback) send(p, "worldeditvisualizer.not_cuboid");
            return;
        }

        // We need both min/max and pos1/pos2 (for coloring/labels)
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        BlockVector3 pos1 = cuboid.getPos1();
        BlockVector3 pos2 = cuboid.getPos2();

        SelectionSnapshot snap = new SelectionSnapshot(p.getWorld().getUID(), min, max, pos1, pos2);
        SelectionSnapshot prev = last.get(p.getUniqueId());
        if (snap.equals(prev)) return; // unchanged

        last.put(p.getUniqueId(), snap);
        renderCuboid(p, snap);
    }

    /* Rendering */

    private void renderCuboid(Player p, SelectionSnapshot s) {
        clear(p); // wipe current displays

        World w = p.getWorld();
        List<Display> list = new ArrayList<>();
        shown.put(p.getUniqueId(), list);

        int minX = s.min.x(); int minY = s.min.y(); int minZ = s.min.z();
        int maxX = s.max.x(); int maxY = s.max.y(); int maxZ = s.max.z();

        // corners: pos1/pos2 highlighted & labeled
        placeCorner(w, p, s.pos1, pos1CornerMat, pos1Glow, labelHash ? "#1" : "pos1", list);
        placeCorner(w, p, s.pos2, pos2CornerMat, pos2Glow, labelHash ? "#2" : "pos2", list);

        // remaining 6 corners
        Set<BlockVector3> used = Set.of(s.pos1, s.pos2);
        int[] xs = new int[]{minX, maxX};
        int[] ys = new int[]{minY, maxY};
        int[] zs = new int[]{minZ, maxZ};
        for (int x : xs) for (int y : ys) for (int z : zs) {
            BlockVector3 c = BlockVector3.at(x, y, z);
            if (used.contains(c)) continue;
            list.add(spawnBlockDisplay(p, w, x + 0.5, y + 0.5, z + 0.5, cornerScale, cornerMat, edgeGlow));
        }

        // edges as dotted line
        // X edges
        for (int y : ys) for (int z : zs)
            for (double x = minX; x <= maxX + 1e-6; x += step)
                list.add(spawnBlockDisplay(p, w, x + 0.5, y + 0.5, z + 0.5, edgeScale, edgeMat, edgeGlow));
        // Y edges
        for (int x : xs) for (int z : zs)
            for (double y = minY; y <= maxY + 1e-6; y += step)
                list.add(spawnBlockDisplay(p, w, x + 0.5, y + 0.5, z + 0.5, edgeScale, edgeMat, edgeGlow));
        // Z edges
        for (int x : xs) for (int y : ys)
            for (double z = minZ; z <= maxZ + 1e-6; z += step)
                list.add(spawnBlockDisplay(p, w, x + 0.5, y + 0.5, z + 0.5, edgeScale, edgeMat, edgeGlow));
    }

    private void placeCorner(World w, Player p, BlockVector3 corner,
                             Material mat, NamedTextColor glow, String label, List<Display> out) {
        double cx = corner.x() + 0.5;
        double cy = corner.y() + 0.5;
        double cz = corner.z() + 0.5;
        out.add(spawnBlockDisplay(p, w, cx, cy, cz, cornerScale, mat, glow));
        if (labelEnabled) {
            out.add(spawnLabel(p, w, cx, cy + labelYOffset, cz, label, glow));
        }
    }

    /** Centered cube: keep entity at block center and translate by (-s/2) so scaled unit cube is centered. */
    private BlockDisplay spawnBlockDisplay(Player p, World w, double x, double y, double z,
                                           float scale, Material mat, NamedTextColor glowColor) {
        BlockDisplay d = w.spawn(new Location(w, x, y, z), BlockDisplay.class, ent -> {
            ent.setBlock(Bukkit.createBlockData(mat));
            ent.setBrightness(new Display.Brightness(15, 15));
            ent.setGlowing(true);
            ent.setGlowColorOverride(toBukkitColor(glowColor)); // FIX: expects org.bukkit.Color
            ent.setShadowRadius(0f);
            ent.setVisibleByDefault(false); // per-player visibility
            float half = scale / 2f;
            ent.setTransformation(new Transformation(
                    new Vector3f(-half, -half, -half),             // center the cube
                    new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
        });
        p.showEntity(feature.getPlugin(), d); // only this player sees it
        return d;
    }

    private TextDisplay spawnLabel(Player p, World w, double x, double y, double z,
                                   String text, NamedTextColor glowColor) {
        TextDisplay t = w.spawn(new Location(w, x, y, z), TextDisplay.class, ent -> {
            ent.text(Component.text(text, glowColor));
            ent.setBillboard(Display.Billboard.CENTER);
            ent.setSeeThrough(true);
            ent.setGlowing(true);
            ent.setGlowColorOverride(toBukkitColor(glowColor)); // FIX: expects org.bukkit.Color
            ent.setBackgroundColor(Color.fromARGB(110, 0, 0, 0)); // FIX: org.bukkit.Color with alpha
            ent.setLineWidth(120);
            ent.setShadowed(true);
            ent.setVisibleByDefault(false); // per-player
            // scale label (uniform)
            ent.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(labelScale, labelScale, labelScale),
                    new Quaternionf()
            ));
        });
        p.showEntity(feature.getPlugin(), t);
        return t;
    }

    /* Helpers / housekeeping */

    private static Color toBukkitColor(NamedTextColor named) {
        if (named == null) return Color.WHITE;
        int rgb = named.value(); // 0xRRGGBB
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Color.fromRGB(r, g, b);
    }

    private void send(Player p, String msgKey) {
        p.sendMessage(feature.getLocalizationHandler().getMessage(msgKey).forAudience(p).build());
    }

    private void disableOffline(UUID uuid) {
        enabled.remove(uuid);
        last.remove(uuid);
        shown.remove(uuid);
    }

    private static Material safeMaterial(String name, Material def) {
        Material m = Material.matchMaterial(Objects.toString(name, ""));
        return m == null ? def : m;
    }

    private static NamedTextColor parseNamedColor(String s, NamedTextColor def) {
        if (s == null) return def;
        var parsed = NamedTextColor.NAMES.value(s);
        return parsed == null ? def : parsed;
    }

    private static float clampScale(float s) {
        if (Float.isNaN(s) || !Float.isFinite(s)) return 0.2f;
        return Math.max(0.01f, Math.min(1.0f, s));
    }

    private record SelectionSnapshot(UUID world, BlockVector3 min, BlockVector3 max,
                                     BlockVector3 pos1, BlockVector3 pos2) {
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SelectionSnapshot s)) return false;
            return Objects.equals(world, s.world) &&
                    min.equals(s.min) && max.equals(s.max) &&
                    pos1.equals(s.pos1) && pos2.equals(s.pos2);
        }
        @Override public int hashCode() { return Objects.hash(world, min, max, pos1, pos2); }
    }
}
