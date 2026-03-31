package nl.hauntedmc.serverfeatures.features.betterdoors.internal;

import nl.hauntedmc.serverfeatures.features.betterdoors.BetterDoors;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;

import java.util.Optional;

public final class BetterDoorsHandler {

    private final BetterDoors feature;

    private final float woodVolume;
    private final float woodPitch;

    private final float otherVolume;
    private final float otherPitch;

    public BetterDoorsHandler(BetterDoors feature) {
        this.feature = feature;

        // Read config here (clean & encapsulated)
        var cfg = feature.getConfigHandler();
        double wVol = cfg.node("knock_wood_volume").as(Double.class, 1.0D);
        double wPit = cfg.node("knock_wood_pitch").as(Double.class, 1.0D);

        double oVol = cfg.node("knock_other_volume").as(Double.class, 1.0D);
        double oPit = cfg.node("knock_other_pitch").as(Double.class, 1.0D);

        this.woodVolume = clampFloat((float) wVol);
        this.woodPitch = clampFloat((float) wPit);

        this.otherVolume = clampFloat((float) oVol);
        this.otherPitch = clampFloat((float) oPit);
    }

    private static float clampFloat(float v) {
        return Math.max((float) 0.0, Math.min(Float.MAX_VALUE, v));
    }

    /* ===== Door pairing helpers ===== */

    /**
     * Normalize to bottom half of a door.
     */
    public Optional<Block> bottomHalf(Block doorBlock) {
        if (!(doorBlock.getBlockData() instanceof Door d)) return Optional.empty();
        if (d.getHalf() == Bisected.Half.BOTTOM) return Optional.of(doorBlock);
        Block below = doorBlock.getRelative(BlockFace.DOWN);
        if (!(below.getBlockData() instanceof Door)) return Optional.empty();
        return Optional.of(below);
    }

    /**
     * Find the paired double-door block (bottom half):
     * - Same door type, same facing, opposite hinge, adjacent perpendicular block.
     */
    public Optional<Block> findPaired(Block bottomDoorBlock) {
        BlockData bd = bottomDoorBlock.getBlockData();
        if (!(bd instanceof Door door)) return Optional.empty();

        final BlockFace facing = door.getFacing();
        final Door.Hinge hinge = door.getHinge();

        BlockFace perpendicular = switch (facing) {
            case NORTH, SOUTH -> (hinge == Door.Hinge.LEFT ? BlockFace.WEST : BlockFace.EAST);
            case EAST, WEST -> (hinge == Door.Hinge.LEFT ? BlockFace.SOUTH : BlockFace.NORTH);
            default -> null;
        };
        if (perpendicular == null) return Optional.empty();

        Block neighbor = bottomDoorBlock.getRelative(perpendicular);
        BlockData nbd = neighbor.getBlockData();
        if (!(nbd instanceof Door other)) return Optional.empty();

        if (neighbor.getType() != bottomDoorBlock.getType()) return Optional.empty();
        if (other.getFacing() != facing) return Optional.empty();
        if (other.getHinge() == hinge) return Optional.empty();

        return bottomHalf(neighbor);
    }

    /**
     * Set open state on a door (both halves move together by vanilla).
     */
    public void setDoorOpen(Block doorBlock, boolean open) {
        BlockData bd = doorBlock.getBlockData();
        if (!(bd instanceof Door d)) return;
        if (d.isOpen() == open) return;
        d.setOpen(open);
        doorBlock.setBlockData(d, true);
    }

    /**
     * Mirror the neighbor **next tick** so we copy the primary’s final state after vanilla processing.
     */
    public void mirrorNextTick(Block primaryDoorAnyHalf) {
        bottomHalf(primaryDoorAnyHalf).ifPresent(primaryBottom ->
                findPaired(primaryBottom).ifPresent(neighborBottom ->
                        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                            BlockData bd = primaryBottom.getBlockData();
                            if (!(bd instanceof Door primary)) return;
                            setDoorOpen(neighborBottom, primary.isOpen());
                        })
                )
        );
    }

    /**
     * Mirror immediately to a known state (used for redstone).
     */
    public void mirrorImmediate(Block primaryDoorAnyHalf, boolean open) {
        bottomHalf(primaryDoorAnyHalf).flatMap(this::findPaired).ifPresent(neighborBottom -> setDoorOpen(neighborBottom, open));
    }

    /* ===== Knock sound ===== */

    public void playKnock(Block doorBlock) {
        boolean isWood = Tag.WOODEN_DOORS.isTagged(doorBlock.getType());
        if (isWood) {
            doorBlock.getWorld().playSound(
                    doorBlock.getLocation().toCenterLocation(),
                    Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                    SoundCategory.BLOCKS,
                    woodVolume,
                    woodPitch
            );
        } else {
            doorBlock.getWorld().playSound(
                    doorBlock.getLocation().toCenterLocation(),
                    Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                    SoundCategory.BLOCKS,
                    otherVolume,
                    otherPitch
            );
        }
    }

    /* ===== Utility ===== */

    public boolean isDoor(Block b) {
        return b != null && b.getBlockData() instanceof Door;
    }
}
