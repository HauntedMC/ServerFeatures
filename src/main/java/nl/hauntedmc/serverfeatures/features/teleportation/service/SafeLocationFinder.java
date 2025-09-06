package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.integration.GriefPreventionHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SafeLocationFinder {

    private final Teleportation feature;
    private final GriefPreventionHook gpHook;
    private final TeleportBounds bounds;

    public SafeLocationFinder(Teleportation feature) {
        this.feature = feature;
        this.gpHook = new GriefPreventionHook();
        this.bounds = new TeleportBounds(feature);
    }

    public int maxAttempts() {
        Object v = feature.getConfigHandler().getSetting("randomtp.max_attempts");
        return (v instanceof Number n) ? n.intValue() : 250;
    }

    private double yOffsetAfterHighest() {
        Object v = feature.getConfigHandler().getSetting("randomtp.y_offset_after_highest");
        return (v instanceof Number n) ? n.doubleValue() : 4.0D;
    }

    private Set<Material> readDisabledMaterials() {
        Object o = feature.getConfigHandler().getSetting("disabled_blocks");
        if (o instanceof java.util.List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(Material::matchMaterial)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        return Set.of(Material.LAVA, Material.WATER, Material.LILY_PAD, Material.CACTUS);
    }

    /** Random safe location:
     * - Inside WorldBorder (outer)
     * - NOT inside inner reserved rect (if configured)
     * - NOT in GP claims
     * - Avoids disabled ground materials
     */
    public Location findRandomSafeLocation(World world) {
        final Set<Material> disabled = readDisabledMaterials();
        final int attempts = maxAttempts();
        final double yOffset = yOffsetAfterHighest();
        final ThreadLocalRandom r = ThreadLocalRandom.current();
        final TeleportBounds.Rect outer = bounds.outerRect(world);
        final Optional<TeleportBounds.Rect> innerOpt = bounds.innerRect();

        for (int i = 0; i < attempts; i++) {
            int x = randomInclusive(outer.minX(), outer.maxX(), r);
            int z = randomInclusive(outer.minZ(), outer.maxZ(), r);

            if (innerOpt.isPresent() && innerOpt.get().contains(x, z)) continue;

            Location loc = world.getHighestBlockAt(x, z).getLocation();
            Block ground = loc.getBlock();

            if (disabled.contains(ground.getType()) ||
                    disabled.contains(ground.getRelative(BlockFace.DOWN).getType())) {
                continue;
            }

            if (gpHook.isInClaim(loc)) continue;

            Location tp = loc.clone().add(0.5, yOffset, 0.5);
            double clampedY = Math.max(world.getMinHeight(), Math.min(tp.getY(), world.getMaxHeight() - 1));
            tp.setY(clampedY);
            return tp;
        }

        return null;
    }

    private int randomInclusive(int min, int max, ThreadLocalRandom r) {
        int bound = (max - min) + 1;
        if (bound <= 0) return min;
        return r.nextInt(bound) + min;
    }
}
