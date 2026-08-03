package nl.hauntedmc.serverfeatures.features.bettercoral.internal;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoralMaterialsTest {

    @Test
    void containsEveryVanillaCoralDryingTransition() {
        Map<Material, Material> transitions = CoralMaterials.dryingTransitions();

        assertEquals(20, transitions.size());
        assertTransition(transitions, Material.TUBE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK);
        assertTransition(transitions, Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        assertTransition(transitions, Material.BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        assertTransition(transitions, Material.FIRE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK);
        assertTransition(transitions, Material.HORN_CORAL_BLOCK, Material.DEAD_HORN_CORAL_BLOCK);

        assertTransition(transitions, Material.TUBE_CORAL, Material.DEAD_TUBE_CORAL);
        assertTransition(transitions, Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        assertTransition(transitions, Material.BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        assertTransition(transitions, Material.FIRE_CORAL, Material.DEAD_FIRE_CORAL);
        assertTransition(transitions, Material.HORN_CORAL, Material.DEAD_HORN_CORAL);

        assertTransition(transitions, Material.TUBE_CORAL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        assertTransition(transitions, Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        assertTransition(transitions, Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        assertTransition(transitions, Material.FIRE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        assertTransition(transitions, Material.HORN_CORAL_FAN, Material.DEAD_HORN_CORAL_FAN);

        assertTransition(transitions, Material.TUBE_CORAL_WALL_FAN, Material.DEAD_TUBE_CORAL_WALL_FAN);
        assertTransition(transitions, Material.BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_WALL_FAN);
        assertTransition(transitions, Material.BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_WALL_FAN);
        assertTransition(transitions, Material.FIRE_CORAL_WALL_FAN, Material.DEAD_FIRE_CORAL_WALL_FAN);
        assertTransition(transitions, Material.HORN_CORAL_WALL_FAN, Material.DEAD_HORN_CORAL_WALL_FAN);
    }

    @Test
    void furnaceConversionsContainOnlyRealInventoryItems() {
        Map<Material, Material> conversions = CoralMaterials.furnaceConversions();

        assertEquals(15, conversions.size());
        conversions.forEach((input, result) -> {
            assertTrue(input.isItem(), () -> input + " is not an item");
            assertTrue(result.isItem(), () -> result + " is not an item");
            assertEquals(CoralMaterials.dryingTransitions().get(input), result);
        });
    }

    @Test
    void wallFanBlockStatesAreProtectedButNeverRegisteredAsRecipes() {
        assertTrue(CoralMaterials.isDryingTransition(
                Material.TUBE_CORAL_WALL_FAN,
                Material.DEAD_TUBE_CORAL_WALL_FAN
        ));
        assertFalse(CoralMaterials.furnaceConversions().containsKey(Material.TUBE_CORAL_WALL_FAN));
        assertFalse(CoralMaterials.furnaceConversions().containsKey(Material.BRAIN_CORAL_WALL_FAN));
        assertFalse(CoralMaterials.furnaceConversions().containsKey(Material.BUBBLE_CORAL_WALL_FAN));
        assertFalse(CoralMaterials.furnaceConversions().containsKey(Material.FIRE_CORAL_WALL_FAN));
        assertFalse(CoralMaterials.furnaceConversions().containsKey(Material.HORN_CORAL_WALL_FAN));
    }

    @Test
    void onlyTheMatchingDeadStateCountsAsDrying() {
        assertTrue(CoralMaterials.isDryingTransition(
                Material.FIRE_CORAL,
                Material.DEAD_FIRE_CORAL
        ));
        assertFalse(CoralMaterials.isDryingTransition(Material.FIRE_CORAL, Material.AIR));
        assertFalse(CoralMaterials.isDryingTransition(
                Material.FIRE_CORAL,
                Material.DEAD_BRAIN_CORAL
        ));
    }

    @Test
    void publishedMappingsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> CoralMaterials.dryingTransitions().put(
                Material.STONE,
                Material.DIRT
        ));
        assertThrows(UnsupportedOperationException.class, () -> CoralMaterials.furnaceConversions().clear());
    }

    private static void assertTransition(
            Map<Material, Material> transitions,
            Material live,
            Material dead
    ) {
        assertEquals(dead, transitions.get(live));
    }
}
