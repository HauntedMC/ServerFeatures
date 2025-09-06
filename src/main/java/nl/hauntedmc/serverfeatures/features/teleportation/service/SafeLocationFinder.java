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

    public SafeLocationFinder(Teleportation feature, TeleportBounds bounds) {
        this.feature = feature;
        this.gpHook = new GriefPreventionHook();
        this.bounds = bounds;
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

    /* ----------------------------- */
    /* Random safe location */
    /* ----------------------------- */

    /**
     * Random safe location:
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

            if (disabled.contains(ground.getType()) || disabled.contains(ground.getRelative(BlockFace.DOWN).getType())) {
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

    /* ----------------------------- */
    /* /tppos safe resolution */
    /* ----------------------------- */

    /**
     * Resolve a safe standing spot for /tppos at X/Z with a given Y:
     *
     * - If Y is above the current surface: place the player on top of the surface (if safe).
     * - Otherwise: search downward from Y for the nearest safe floor.
     * - "Safe" means: solid non-disabled ground below; feet and head are clear and non-liquid.
     * - Returns null if none is found (caller should inform the user to adjust coordinates).
     */
    public Location findSafeForTpPos(World world, int x, int y, int z) {
        final Set<Material> disabled = readDisabledMaterials();

        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1; // feet must be <= max-1 (head at +1)

        // Center on block
        final double centerX = x + 0.5;
        final double centerZ = z + 0.5;

        // Current "surface" top (highest solid/fluid block), feet would stand at +1
        final int surfaceFeetY = world.getHighestBlockYAt(x, z) + 1;

        // Case 1: Above ground → put safely on the ground (if safe)
        if (y >= surfaceFeetY) {
            if (isSafeFeetY(world, x, surfaceFeetY, z, disabled)) {
                return new Location(world, centerX, Math.clamp(surfaceFeetY, minY, maxY), centerZ);
            } else {
                return null;
            }
        }

        // Case 2: Below/inside → search downward from y for nearest safe floor
        int startFeetY = Math.clamp(y, minY + 1, maxY); // feet y; ground at feetY-1
        for (int feetY = startFeetY; feetY >= minY + 1; feetY--) {
            if (isSafeFeetY(world, x, feetY, z, disabled)) {
                return new Location(world, centerX, feetY, centerZ);
            }
        }

        return null;
    }

    private boolean isSafeFeetY(World world, int x, int feetY, int z, Set<Material> disabled) {
        // Ensure head fits
        if (feetY + 1 >= world.getMaxHeight()) return false;

        Block ground = world.getBlockAt(x, feetY - 1, z);
        Block feet   = world.getBlockAt(x, feetY, z);
        Block head   = world.getBlockAt(x, feetY + 1, z);

        // Ground must be solid and not passable, and not in disabled list
        var groundType = ground.getType();
        boolean solidGround = groundType.isSolid() && !ground.isPassable() && !disabled.contains(groundType);

        // Feet & head must be clear and not liquid
        boolean feetClear = isClearSpace(feet);
        boolean headClear = isClearSpace(head);

        return solidGround && feetClear && headClear;
    }

    private boolean isClearSpace(Block b) {
        var t = b.getType();
        if (t.isAir()) return true;
        if (b.isLiquid()) return false;
        return b.isPassable();
    }
}
