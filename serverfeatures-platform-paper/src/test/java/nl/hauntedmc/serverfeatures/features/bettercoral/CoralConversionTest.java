package nl.hauntedmc.serverfeatures.features.bettercoral;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoralConversionTest {

    @Test
    void exposesExactlyTheFifteenSmeltableInventoryForms() {
        assertEquals(15, CoralConversion.itemConversions().size());
        CoralConversion.itemConversions().forEach((live, dead) -> {
            assertTrue(live.isItem(), () -> live + " must be a valid furnace input item");
            assertTrue(dead.isItem(), () -> dead + " must be a valid furnace result item");
        });
    }

    @Test
    void protectsWallFansWithoutTryingToRegisterImpossibleWallFanRecipes() {
        assertTrue(CoralConversion.isLiveCoralBlock(Material.TUBE_CORAL_WALL_FAN));
        assertTrue(CoralConversion.isLiveCoralBlock(Material.HORN_CORAL_WALL_FAN));
        assertFalse(CoralConversion.itemConversions().containsKey(Material.TUBE_CORAL_WALL_FAN));
        assertFalse(CoralConversion.itemConversions().containsKey(Material.HORN_CORAL_WALL_FAN));
    }

    @Test
    void mapsEveryInventoryFormToItsMatchingDeadEquivalent() {
        assertEquals(Material.DEAD_TUBE_CORAL_BLOCK,
                CoralConversion.itemConversions().get(Material.TUBE_CORAL_BLOCK));
        assertEquals(Material.DEAD_BRAIN_CORAL,
                CoralConversion.itemConversions().get(Material.BRAIN_CORAL));
        assertEquals(Material.DEAD_FIRE_CORAL_FAN,
                CoralConversion.itemConversions().get(Material.FIRE_CORAL_FAN));
    }
}
