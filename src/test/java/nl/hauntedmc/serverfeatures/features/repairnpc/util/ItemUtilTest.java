package nl.hauntedmc.serverfeatures.features.repairnpc.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemUtilTest {

    @Test
    void isToolDetectsSupportedToolTypes() {
        assertTrue(ItemUtil.isTool(stack(Material.DIAMOND_PICKAXE)));
        assertTrue(ItemUtil.isTool(stack(Material.SHEARS)));
        assertFalse(ItemUtil.isTool(stack(Material.DIRT)));
    }

    @Test
    void isArmorDetectsSupportedArmorTypes() {
        assertTrue(ItemUtil.isArmor(stack(Material.NETHERITE_HELMET)));
        assertTrue(ItemUtil.isArmor(stack(Material.SHIELD)));
        assertFalse(ItemUtil.isArmor(stack(Material.STICK)));
    }

    private static ItemStack stack(Material material) {
        return new MaterialOnlyStack(material);
    }

    private static final class MaterialOnlyStack extends ItemStack {

        private final Material material;

        private MaterialOnlyStack(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }
    }
}
