package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.integration.GriefPreventionHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.*;
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
    /* Random safe location          */
    /* ----------------------------- */

    /**
     * Random safe location:
     * - Sample ONLY inside (effective outer minus effective inner)
     * - Avoid GP claims (conservative)
     * - Avoid disabled ground materials at/under the highest block
     * - Final guard: enforce outer/inner constraints before returning
     */
    public Location findRandomSafeLocation(World world) {
        final Set<Material> disabled = readDisabledMaterials();
        final int attempts = maxAttempts();
        final double yOffset = yOffsetAfterHighest();
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        final TeleportBounds.Rect outer = bounds.effectiveOuterRect(world);
        if (outer.isEmpty()) return null;

        final Optional<TeleportBounds.Rect> innerOpt = bounds.innerRect();
        final List<TeleportBounds.Rect> allowed = allowedRings(outer, innerOpt);
        if (allowed.isEmpty()) return null;

        // Precompute positive-area rects + cumulative areas for weighted sampling
        final List<TeleportBounds.Rect> rects = new ArrayList<>(allowed.size());
        final List<Long> cumulative = new ArrayList<>(allowed.size());
        long total = 0L;
        for (TeleportBounds.Rect r : allowed) {
            long a = area(r);
            if (a <= 0L) continue;
            rects.add(r);
            total += a;
            cumulative.add(total);
        }
        if (total <= 0L || rects.isEmpty()) return null;

        for (int i = 0; i < attempts; i++) {
            long pick = rnd.nextLong(total); // 0..total-1
            int idx = lowerBound(cumulative, pick);
            TeleportBounds.Rect sel = rects.get(idx);

            int x = randomInclusive(sel.minX(), sel.maxX(), rnd);
            int z = randomInclusive(sel.minZ(), sel.maxZ(), rnd);

            // Hard guard: never allow inner; also ensure we are inside effective outer
            if (innerOpt.isPresent() && innerOpt.get().contains(x, z)) continue;
            if (!outer.contains(x, z)) continue;

            Block highest = world.getHighestBlockAt(x, z);
            Block ground = highest.getLocation().getBlock();

            if (disabled.contains(ground.getType()) || disabled.contains(ground.getRelative(BlockFace.DOWN).getType())) {
                continue;
            }

            if (gpHook.isInClaim(highest.getLocation())) continue;

            Location tp = highest.getLocation().add(0.5, yOffset, 0.5);
            double clampedY = Math.clamp(tp.getY(), world.getMinHeight(), world.getMaxHeight() - 1);
            tp.setY(clampedY);
            return tp;
        }
        return null;
    }

    /** outer − (inner ∩ outer) as up to four non-overlapping bands. */
    private List<TeleportBounds.Rect> allowedRings(TeleportBounds.Rect outer, Optional<TeleportBounds.Rect> innerOpt) {
        List<TeleportBounds.Rect> out = new ArrayList<>(4);

        if (innerOpt.isEmpty()) {
            out.add(outer);
            return out;
        }

        TeleportBounds.Rect inner = innerOpt.get();
        TeleportBounds.Rect i = outer.intersect(inner);
        if (i.isEmpty()) {
            out.add(outer);
            return out;
        }

        addIfNonEmpty(out, new TeleportBounds.Rect(outer.minX(), outer.maxX(), i.maxZ() + 1, outer.maxZ())); // top
        addIfNonEmpty(out, new TeleportBounds.Rect(outer.minX(), outer.maxX(), outer.minZ(), i.minZ() - 1)); // bottom
        addIfNonEmpty(out, new TeleportBounds.Rect(outer.minX(), i.minX() - 1, i.minZ(), i.maxZ()));         // left
        addIfNonEmpty(out, new TeleportBounds.Rect(i.maxX() + 1, outer.maxX(), i.minZ(), i.maxZ()));         // right

        return out;
    }

    private void addIfNonEmpty(List<TeleportBounds.Rect> list, TeleportBounds.Rect r) {
        if (!r.isEmpty()) list.add(r);
    }

    private long area(TeleportBounds.Rect r) {
        long w = (long) r.maxX() - (long) r.minX() + 1L;
        long h = (long) r.maxZ() - (long) r.minZ() + 1L;
        if (w <= 0L || h <= 0L) return 0L;
        return w * h;
    }

    /** first index i where cumulative[i] > pick (since pick ∈ [0,total-1]) */
    private int lowerBound(List<Long> cumulative, long pick) {
        int lo = 0, hi = cumulative.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pick < cumulative.get(mid)) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    private int randomInclusive(int min, int max, ThreadLocalRandom r) {
        long bound = ((long) max - (long) min) + 1L;
        if (bound <= 0L) return min; // degenerate
        return (int) (r.nextLong(bound) + min);
    }

    /* ----------------------------- */
    /* /tppos safe resolution        */
    /* ----------------------------- */

    public Location findSafeForTpPos(World world, int x, int y, int z) {
        final Set<Material> disabled = readDisabledMaterials();

        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1; // feet must be <= max-1 (head at +1)

        final double centerX = x + 0.5;
        final double centerZ = z + 0.5;

        final int surfaceFeetY = world.getHighestBlockYAt(x, z) + 1;

        if (y >= surfaceFeetY) {
            int feetY = Math.clamp(surfaceFeetY, minY + 1, maxY);
            if (isSafeFeetY(world, x, feetY, z, disabled)) {
                return new Location(world, centerX, feetY, centerZ);
            } else {
                return null;
            }
        }

        int startFeetY = Math.clamp(y, minY + 1, maxY);
        for (int feetY = startFeetY; feetY >= minY + 1; feetY--) {
            if (isSafeFeetY(world, x, feetY, z, disabled)) {
                return new Location(world, centerX, feetY, centerZ);
            }
        }

        return null;
    }

    private boolean isSafeFeetY(World world, int x, int feetY, int z, Set<Material> disabled) {
        if (feetY + 1 >= world.getMaxHeight()) return false;

        Block ground = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);

        var groundType = ground.getType();
        boolean solidGround = groundType.isSolid() && !ground.isPassable() && !disabled.contains(groundType);

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
