package nl.hauntedmc.serverfeatures.features.repairnpc.util;

import org.bukkit.inventory.ItemStack;

public class ItemUtil {

    public static boolean isTool(ItemStack item) {
        return switch (item.getType()) {
            case WOODEN_PICKAXE, WOODEN_SHOVEL, WOODEN_HOE, WOODEN_SWORD, WOODEN_AXE, STONE_PICKAXE, STONE_SHOVEL,
                 STONE_HOE, STONE_SWORD, STONE_AXE, GOLDEN_PICKAXE, GOLDEN_SHOVEL, GOLDEN_HOE, GOLDEN_SWORD, GOLDEN_AXE,
                 IRON_PICKAXE, IRON_SHOVEL, IRON_HOE, IRON_SWORD, IRON_AXE, DIAMOND_PICKAXE, DIAMOND_SHOVEL,
                 DIAMOND_HOE, DIAMOND_SWORD, DIAMOND_AXE, NETHERITE_PICKAXE, NETHERITE_SHOVEL, NETHERITE_HOE,
                 NETHERITE_SWORD, NETHERITE_AXE, BOW, CROSSBOW, TRIDENT, FLINT_AND_STEEL, FISHING_ROD,
                 WARPED_FUNGUS_ON_A_STICK, CARROT_ON_A_STICK, SHEARS -> true;
            default -> false;
        };
    }

    public static boolean isArmor(ItemStack item) {
        return switch (item.getType()) {
            case TURTLE_HELMET, SHIELD, ELYTRA, LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS,
                 CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS, GOLDEN_HELMET,
                 GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS, IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS,
                 IRON_BOOTS, DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS, NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> true;
            default -> false;
        };
    }
}
