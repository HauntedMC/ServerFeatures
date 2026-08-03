package nl.hauntedmc.serverfeatures.features.bettercoral;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical live-to-dead coral mapping used by both fade prevention and recipes.
 */
public final class CoralConversion {

    private static final Map<Material, Material> ITEM_CONVERSIONS;
    private static final Set<Material> LIVE_BLOCKS;

    static {
        EnumMap<Material, Material> itemConversions = new EnumMap<>(Material.class);
        put(itemConversions, Material.TUBE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK);
        put(itemConversions, Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        put(itemConversions, Material.BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        put(itemConversions, Material.FIRE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK);
        put(itemConversions, Material.HORN_CORAL_BLOCK, Material.DEAD_HORN_CORAL_BLOCK);

        put(itemConversions, Material.TUBE_CORAL, Material.DEAD_TUBE_CORAL);
        put(itemConversions, Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        put(itemConversions, Material.BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        put(itemConversions, Material.FIRE_CORAL, Material.DEAD_FIRE_CORAL);
        put(itemConversions, Material.HORN_CORAL, Material.DEAD_HORN_CORAL);

        put(itemConversions, Material.TUBE_CORAL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        put(itemConversions, Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        put(itemConversions, Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        put(itemConversions, Material.FIRE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        put(itemConversions, Material.HORN_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN);
        ITEM_CONVERSIONS = Collections.unmodifiableMap(itemConversions);

        EnumSet<Material> liveBlocks = EnumSet.copyOf(itemConversions.keySet());
        liveBlocks.add(Material.TUBE_CORAL_WALL_FAN);
        liveBlocks.add(Material.BRAIN_CORAL_WALL_FAN);
        liveBlocks.add(Material.BUBBLE_CORAL_WALL_FAN);
        liveBlocks.add(Material.FIRE_CORAL_WALL_FAN);
        liveBlocks.add(Material.HORN_CORAL_WALL_FAN);
        LIVE_BLOCKS = Collections.unmodifiableSet(liveBlocks);
    }

    private CoralConversion() {
    }

    private static void put(Map<Material, Material> conversions, Material live, Material dead) {
        if (!live.isItem() || !dead.isItem()) {
            throw new IllegalStateException("Coral furnace conversion must use item materials: " + live + " -> " + dead);
        }
        conversions.put(live, dead);
    }

    public static Map<Material, Material> itemConversions() {
        return ITEM_CONVERSIONS;
    }

    public static boolean isLiveCoralBlock(Material material) {
        return LIVE_BLOCKS.contains(material);
    }
}
