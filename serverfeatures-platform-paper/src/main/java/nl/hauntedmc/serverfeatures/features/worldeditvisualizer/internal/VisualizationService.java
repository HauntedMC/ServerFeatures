package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.ui.world.display.VisualHandle;
import nl.hauntedmc.serverfeatures.api.ui.world.display.Visualisation;
import nl.hauntedmc.serverfeatures.api.ui.world.display.options.VisualOptions;
import nl.hauntedmc.serverfeatures.api.ui.world.display.shape.CuboidRegionShape;
import nl.hauntedmc.serverfeatures.api.ui.world.display.visualisation.CubeRegionVisualisation;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges WorldEdit selections to the visualisation API.
 * Converts a WE cuboid to a {@link CuboidRegionShape} and renders via {@link Visualisation}.
 */
public final class VisualizationService {

    private final WorldEditVisualizer feature;
    private final Visualisation visualiser;

    // per-player
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SelectionSnapshot> last = new ConcurrentHashMap<>();
    private final Map<UUID, VisualHandle> shown = new ConcurrentHashMap<>();

    public VisualizationService(WorldEditVisualizer feature) {
        this.feature = feature;
        this.visualiser = new CubeRegionVisualisation(feature.getPlugin());
    }

    /* Public API */

    public boolean isEnabled(Player p) {
        return enabled.contains(p.getUniqueId());
    }

    /**
     * Active if enabled OR a visual handle exists (guards against leftover renders).
     */
    public boolean isActive(Player p) {
        UUID id = p.getUniqueId();
        return enabled.contains(id) || shown.containsKey(id);
    }

    /**
     * Idempotent toggle: if active -> disable+clear, else -> clear stale, enable+render once.
     */
    public boolean toggle(Player p) {
        if (isActive(p)) {
            disable(p, true);
            return false; // now disabled
        } else {
            clear(p);
            last.remove(p.getUniqueId());
            enable(p);
            return true; // now enabled
        }
    }

    /**
     * Enable; always ensures only a single fresh render exists.
     */
    public boolean enable(Player p) {
        clear(p);
        last.remove(p.getUniqueId());
        boolean changed = enabled.add(p.getUniqueId());
        tryShowFromSelection(p, false);
        return changed;
    }

    public boolean disable(Player p, boolean clearNow) {
        boolean changed = enabled.remove(p.getUniqueId());
        last.remove(p.getUniqueId());
        if (clearNow) clear(p);
        return changed;
    }

    public void clear(Player p) {
        VisualHandle handle = shown.remove(p.getUniqueId());
        if (handle != null) handle.clear();
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

        // Build snapshot for diffing
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        BlockVector3 pos1 = cuboid.getPos1();
        BlockVector3 pos2 = cuboid.getPos2();

        SelectionSnapshot snap = new SelectionSnapshot(p.getWorld().getUID(), min, max, pos1, pos2);
        SelectionSnapshot prev = last.get(p.getUniqueId());
        if (snap.equals(prev)) return;

        last.put(p.getUniqueId(), snap);

        // Convert to RegionShape
        Map<String, Vector> named = new LinkedHashMap<>();
        named.put("pos1", new Vector(pos1.x() + 0.5, pos1.y() + 0.5, pos1.z() + 0.5));
        named.put("pos2", new Vector(pos2.x() + 0.5, pos2.y() + 0.5, pos2.z() + 0.5));

        CuboidRegionShape shape = new CuboidRegionShape(
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z(),
                named
        );

        // Resolve VisualOptions from config
        VisualOptions options = buildOptionsFromConfig();

        // Clear old, render new (ensures single handle)
        clear(p);
        VisualHandle handle = visualiser.show(p, shape, options);
        shown.put(p.getUniqueId(), handle);
    }

    /* Options mapping */

    private VisualOptions buildOptionsFromConfig() {
        // Materials
        Material edgeMat = safeMaterial(feature.getString("edge.material", "WHITE_STAINED_GLASS"), Material.WHITE_STAINED_GLASS);
        Material cornerMat = safeMaterial(feature.getString("corner.material", "LIME_STAINED_GLASS"), Material.LIME_STAINED_GLASS);
        Material pos1Mat = safeMaterial(feature.getString("corner.pos1_material", "BLUE_STAINED_GLASS"), Material.BLUE_STAINED_GLASS);
        Material pos2Mat = safeMaterial(feature.getString("corner.pos2_material", "RED_STAINED_GLASS"), Material.RED_STAINED_GLASS);

        // Glow
        NamedTextColor edgeGlow = parseNamedColor(feature.getString("glow.edge_color", "aqua"), NamedTextColor.AQUA);
        NamedTextColor cornerGlow = parseNamedColor(feature.getString("glow.corner_color", "aqua"), NamedTextColor.AQUA);
        NamedTextColor pos1Glow = parseNamedColor(feature.getString("glow.pos1_color", "blue"), NamedTextColor.BLUE);
        NamedTextColor pos2Glow = parseNamedColor(feature.getString("glow.pos2_color", "red"), NamedTextColor.RED);

        // Scales / step
        double step = Math.max(0.25d, feature.getDouble("edge.step_blocks", 0.5d));
        float edgeScale = (float) feature.getDouble("edge.scale", 0.18d);
        float cornerScale = (float) feature.getDouble("corner.scale", 0.45d);

        // Labels
        boolean labelEnabled = feature.getBoolean("label.enabled", true);
        double labelYOffset = feature.getDouble("label.y_offset", 0.7d);
        float labelScale = (float) feature.getDouble("label.scale", 1.0d);
        boolean showHash = feature.getBoolean("label.show_prefix_hash", false);

        return VisualOptions.builder()
                .edgeMaterial(edgeMat)
                .cornerMaterial(cornerMat)
                .namedPointMaterial("pos1", pos1Mat)
                .namedPointMaterial("pos2", pos2Mat)
                .edgeGlow(edgeGlow)
                .cornerGlow(cornerGlow)
                .namedPointGlow("pos1", pos1Glow)
                .namedPointGlow("pos2", pos2Glow)
                .edgeScale(edgeScale)
                .cornerScale(cornerScale)
                .edgeStepBlocks(step)
                .labelsEnabled(labelEnabled)
                .labelYOffset(labelYOffset)
                .labelScale(labelScale)
                .labelTextStrategy(showHash
                        ? (key, idx) -> key.equalsIgnoreCase("pos1") ? "#1" : key.equalsIgnoreCase("pos2") ? "#2" : key
                        : (key, idx) -> key)
                .build();
    }

    /* Helpers */

    private void send(Player p, String msgKey) {
        p.sendMessage(feature.getLocalizationHandler().getMessage(msgKey).forAudience(p).build());
    }

    private void disableOffline(UUID uuid) {
        enabled.remove(uuid);
        last.remove(uuid);
        VisualHandle h = shown.remove(uuid);
        if (h != null) h.clear();
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

    private record SelectionSnapshot(UUID world, BlockVector3 min, BlockVector3 max,
                                     BlockVector3 pos1, BlockVector3 pos2) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SelectionSnapshot(
                    UUID world1, BlockVector3 min1, BlockVector3 max1, BlockVector3 pos3, BlockVector3 pos4
            ))) return false;
            return Objects.equals(world, world1) &&
                    min.equals(min1) && max.equals(max1) &&
                    pos1.equals(pos3) && pos2.equals(pos4);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, min, max, pos1, pos2);
        }
    }
}
