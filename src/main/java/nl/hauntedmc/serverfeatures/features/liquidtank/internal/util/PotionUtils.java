package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Objects;

public class PotionUtils {
	public static boolean isWaterBottle(ItemStack paramItemStack) {
		return paramItemStack.getType() == Material.POTION && Objects.requireNonNull(((PotionMeta) paramItemStack.getItemMeta()).getBasePotionData()).getType() == PotionType.WATER;
	}
}
