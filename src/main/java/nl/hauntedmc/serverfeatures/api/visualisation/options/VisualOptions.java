package nl.hauntedmc.serverfeatures.api.visualisation.options;

import org.bukkit.Material;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder-style configuration for a visualisation.
 * Keeps rendering options orthogonal to geometry for reusability.
 */
public final class VisualOptions {

    // Materials
    private final Material edgeMaterial;
    private final Material cornerMaterial;
    private final Map<String, Material> namedPointMaterials; // e.g., "pos1" -> BLUE_STAINED_GLASS

    // Glow colors
    private final NamedTextColor edgeGlow;
    private final NamedTextColor cornerGlow;
    private final Map<String, NamedTextColor> namedPointGlow; // e.g., "pos2" -> RED

    // Scales / step
    private final float edgeScale;   // 0..1 size of edge dots
    private final float cornerScale; // 0..1 size of corner cubes
    private final double edgeStepBlocks; // spacing in blocks between edge dots

    // Labels
    private final boolean labelEnabled;
    private final double labelYOffset;
    private final float labelScale;
    private final LabelTextStrategy labelTextStrategy;

    public interface LabelTextStrategy {
        /**
         * Produce label text for a named point, given the key (e.g., "pos1") and index (0-based) if relevant.
         * Return null to omit a label for that point.
         */
        String labelFor(String namedKey, int index);
    }

    private VisualOptions(Builder b) {
        this.edgeMaterial = b.edgeMaterial;
        this.cornerMaterial = b.cornerMaterial;
        this.namedPointMaterials = Map.copyOf(b.namedPointMaterials);
        this.edgeGlow = b.edgeGlow;
        this.cornerGlow = b.cornerGlow;
        this.namedPointGlow = Map.copyOf(b.namedPointGlow);
        this.edgeScale = b.edgeScale;
        this.cornerScale = b.cornerScale;
        this.edgeStepBlocks = b.edgeStepBlocks;
        this.labelEnabled = b.labelEnabled;
        this.labelYOffset = b.labelYOffset;
        this.labelScale = b.labelScale;
        this.labelTextStrategy = b.labelTextStrategy;
    }

    public Material edgeMaterial() { return edgeMaterial; }
    public Material cornerMaterial() { return cornerMaterial; }
    public Material materialForNamedPoint(String key) {
        return namedPointMaterials.getOrDefault(key, cornerMaterial);
    }

    public NamedTextColor edgeGlow() { return edgeGlow; }
    public NamedTextColor cornerGlow() { return cornerGlow; }
    public NamedTextColor glowForNamedPoint(String key) {
        return namedPointGlow.getOrDefault(key, cornerGlow);
    }

    public float edgeScale() { return edgeScale; }
    public float cornerScale() { return cornerScale; }
    public double edgeStepBlocks() { return edgeStepBlocks; }

    public boolean labelEnabled() { return labelEnabled; }
    public double labelYOffset() { return labelYOffset; }
    public float labelScale() { return labelScale; }
    public LabelTextStrategy labelTextStrategy() { return labelTextStrategy; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Material edgeMaterial = Material.WHITE_STAINED_GLASS;
        private Material cornerMaterial = Material.LIME_STAINED_GLASS;
        private final Map<String, Material> namedPointMaterials = new HashMap<>();

        private NamedTextColor edgeGlow = NamedTextColor.AQUA;
        private NamedTextColor cornerGlow = NamedTextColor.AQUA;
        private final Map<String, NamedTextColor> namedPointGlow = new HashMap<>();

        private float edgeScale = 0.18f;
        private float cornerScale = 0.45f;
        private double edgeStepBlocks = 0.5d;

        private boolean labelEnabled = true;
        private double labelYOffset = 0.7d;
        private float labelScale = 1.0f;
        private LabelTextStrategy labelTextStrategy = (key, idx) -> key; // default: show the key

        private static float clampScale(float s) {
            if (Float.isNaN(s) || !Float.isFinite(s)) return 0.2f;
            return Math.max(0.01f, Math.min(1.0f, s));
        }

        public Builder edgeMaterial(Material m) { this.edgeMaterial = Objects.requireNonNull(m); return this; }
        public Builder cornerMaterial(Material m) { this.cornerMaterial = Objects.requireNonNull(m); return this; }
        public Builder namedPointMaterial(String key, Material m) { this.namedPointMaterials.put(key, Objects.requireNonNull(m)); return this; }

        public Builder edgeGlow(NamedTextColor c) { this.edgeGlow = Objects.requireNonNull(c); return this; }
        public Builder cornerGlow(NamedTextColor c) { this.cornerGlow = Objects.requireNonNull(c); return this; }
        public Builder namedPointGlow(String key, NamedTextColor c) { this.namedPointGlow.put(key, Objects.requireNonNull(c)); return this; }

        public Builder edgeScale(float s) { this.edgeScale = clampScale(s); return this; }
        public Builder cornerScale(float s) { this.cornerScale = clampScale(s); return this; }
        public Builder edgeStepBlocks(double step) { this.edgeStepBlocks = Math.max(0.25d, step); return this; }

        public Builder labelsEnabled(boolean enabled) { this.labelEnabled = enabled; return this; }
        public Builder labelYOffset(double y) { this.labelYOffset = y; return this; }
        public Builder labelScale(float s) { this.labelScale = Math.max(0.25f, s); return this; }
        public Builder labelTextStrategy(LabelTextStrategy strategy) { this.labelTextStrategy = Objects.requireNonNull(strategy); return this; }

        public VisualOptions build() { return new VisualOptions(this); }
    }
}
