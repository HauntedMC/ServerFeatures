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

        add(transitions, Material.TUBE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK);
        add(transitions, Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        add(transitions, Material.BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        add(transitions, Material.FIRE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK);
        add(transitions, Material.HORN_CORAL_BLOCK, Material.DEAD_HORN_CORAL_BLOCK);

        add(transitions, Material.TUBE_CORAL, Material.DEAD_TUBE_CORAL);
        add(transitions, Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        add(transitions, Material.BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        add(transitions, Material.FIRE_CORAL, Material.DEAD_FIRE_CORAL);
        add(transitions, Material.HORN_CORAL, Material.DEAD_HORN_CORAL);

        add(transitions, Material.TUBE_CORAL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        add(transitions, Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        add(transitions, Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        add(transitions, Material.FIRE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        add(transitions, Material.HORN_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN);

        add(transitions, Material.TUBE_CORAL_WALL_FAN, Material.DEAD_TUBE_CORAL_WALL_FAN);
        add(transitions, Material.BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_WALL_FAN);
        add(transitions, Material.BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_WALL_FAN);
        add(transitions, Material.FIRE_CORAL_WALL_FAN, Material.DEAD_FIRE_CORAL_WALL_FAN);
        add(transitions, Material.HORN_CORAL_WALL_FAN, Material.DEAD_HORN_CORAL_WALL_FAN);

        return Collections.unmodifiableMap(transitions);
    }

    private static Map<Material, Material> createFurnaceConversions() {
        EnumMap<Material, Material> conversions = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Material> transition : DRYING_TRANSITIONS.entrySet()) {
            Material input = transition.getKey();
            Material result = transition.getValue();
            if (input.isItem() && result.isItem()) {
                conversions.put(input, result);
            }
        }
        return Collections.unmodifiableMap(conversions);
    }

    private static void add(Map<Material, Material> transitions, Material live, Material dead) {
        if (!live.isBlock() || !dead.isBlock()) {
            throw new IllegalArgumentException("Coral drying mappings must contain block materials");
        }
        transitions.put(live, dead);
    }
}
