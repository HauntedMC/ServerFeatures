package nl.hauntedmc.serverfeatures.features.repairnpc.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import nl.hauntedmc.serverfeatures.features.repairnpc.RepairNPC;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class EcoUtil {

    private static ConfigurationSection getSection(String key) {
        return (ConfigurationSection) RepairNPC
                .getInstance()
                .getConfigHandler()
                .getSetting(key);
    }

    public static boolean doesPlayerHaveEnough(Player p, Economy eco) {
        double cost = getCost(p.getInventory().getItemInMainHand());
        return eco.getBalance(p) >= cost;
    }

    public static String formatCost(Player p, Economy eco) {
        return eco.format(getCost(p.getInventory().getItemInMainHand()));
    }

    public static void withdraw(Player p, Economy eco) {
        eco.withdrawPlayer(p, getCost(p.getInventory().getItemInMainHand()));
    }

    private static double getCost(ItemStack item) {
        String key = item.getType().name().toLowerCase().replace('_', '-');

        ConfigurationSection bp  = getSection("base-prices");
        ConfigurationSection pdp = getSection("price-per-durability-point");
        ConfigurationSection em  = getSection("enchantment-modifiers");

        // base price (or default)
        double basePrice = bp.getDouble(key, bp.getDouble("default"));

        // durability multiplier (or default)
        double perPoint = pdp.getDouble(key, pdp.getDouble("default"));
        ItemMeta itemMeta = item.getItemMeta();
        Damageable damageItemMeta =  (Damageable) itemMeta;
        double durabilityCost = damageItemMeta.getDamage() * perPoint;

        // enchantment modifiers
        double enchantmentCost = 0.0;
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            String ek = e.getKey().toString().toLowerCase().replace('_', '-');
            double mod = em.getDouble(ek, em.getDouble("default"));
            enchantmentCost += mod * e.getValue();
        }

        return basePrice + durabilityCost + enchantmentCost;
    }
}
