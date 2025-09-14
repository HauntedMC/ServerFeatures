package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ItemCreator {
	public static ItemStack newItem(Material paramMaterial, int paramInt, String paramString1, String paramString2) {
		ItemStack itemStack = new ItemStack(paramMaterial);
		itemStack.setAmount(paramInt);
		ItemMeta itemMeta = itemStack.getItemMeta();
		if (!paramString2.isEmpty()) {
			String[] arrayOfString = paramString2.split("||");
			List<String> arrayList = Arrays.asList(arrayOfString);
			itemMeta.setLore(arrayList);
		}
		if (!paramString1.isEmpty())
			itemMeta.setDisplayName(paramString1);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}
}
