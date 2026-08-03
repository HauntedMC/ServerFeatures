package nl.hauntedmc.serverfeatures.features.bettercoral.internal;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Canonical live-to-dead coral mappings.
 *
 * <p>World drying transitions include wall-fan block states. Furnace conversions deliberately do
 * not: wall fans have no inventory item and are placed from the corresponding floor-fan item.</p>
 */
public final class CoralMaterials {

    private static final Map<Material, Material> DRYING_TRANSITIONS = createDryingTransitions();
    private static final Map<Material, Material> FURNACE_CONVERSIONS = createFurnaceConversions();

    private CoralMaterials() {
    }

    public static Map<Material, Material> dryingTransitions() {
        return DRYING_TRANSITIONS;
    }

    public static Map<Material, Material> furnaceConversions() {
        return FURNACE_CONVERSIONS;
    }

    public static boolean isDryingTransition(Material current, Material next) {
        return DRYING_TRANSITIONS.get(current) == next;
    }

    private static Map<Material, Material> createDryingTransitions() {
        EnumMap<Material, Material> transitions = new EnumMap<>(Material.class);

        put(transitions, Material.TUBE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK);
        put(transitions, Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        put(transitions, Material.BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        put(transitions, Material.FIRE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK);
        put(transitions, Material.HORN_CORAL_BLOCK, Material.DEAD_HORN_CORAL_BLOCK);

        put(transitions, Material.TUBE_CORAL, Material.DEAD_TUBE_CORAL);
        put(transitions, Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        put(transitions, Material.BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        put(transitions, Material.FIRE_CORAL, Material.DEAD_FIRE_CORAL);
        put(transitions, Material.HORN_CORAL, Material.DEAD_HORN_CORAL);

        put(transitions, Material.TUBE_CORAL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        put(transitions, Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        put(transitions, Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        put(transitions, Material.FIRE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        put(transitions, Material.HORN_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN);

        put(transitions, Material.TUBE_CORAL_WALL_FAN, Material.DEAD_TUBE_CORAL_WALL_FAN);
        put(transitions, Material.BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_WALL_FAN);
        put(transitions, Material.BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_WALL_FAN);
        put(transitions, Material.FIRE_CORAL_WALL_FAN, Material.DEAD_FIRE_CORAL_WALL_FAN);
        put(transitions, Material.HORN_CORAL_WALL_FAN, Material.DEAD_HORN_CORAL_WALL_FAN);

        return Collections.unmodifiableMap(transitions);
    }

    private static Map<Material, Material> createFurnaceConversions() {
        EnumMap<Material, Material> conversions = new EnumMap<>(Material.class);

        put(conversions, Material.TUBE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK);
        put(conversions, Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        put(conversions, Material.BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        put(conversions, Material.FIRE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK);
        put(conversions, Material.HORN_CORAL_BLOCK, Material.DEAD_HORN_CORAL_BLOCK);

        put(conversions, Material.TUBE_CORAL, Material.DEAD_TUBE_CORAL);
        put(conversions, Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        put(conversions, Material.BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        put(conversions, Material.FIRE_CORAL, Material.DEAD_FIRE_CORAL);
        put(conversions, Material.HORN_CORAL, Material.DEAD_HORN_CORAL);

        put(conversions, Material.TUBE_CORAL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        put(conversions, Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        put(conversions, Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        put(conversions, Material.FIRE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        put(conversions, Material.HORN_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN);

        return Collections.unmodifiableMap(conversions);
    }

    private static void put(Map<Material, Material> mappings, Material live, Material dead) {
        Material previous = mappings.put(live, dead);
        if (previous != null) {
            throw new IllegalStateException("Duplicate BetterCoral mapping for " + live);
        }
    }
}
