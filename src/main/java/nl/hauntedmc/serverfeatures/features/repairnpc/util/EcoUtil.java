package nl.hauntedmc.serverfeatures.features.repairnpc.util;

import net.milkbowl.vault.economy.Economy;
import nl.hauntedmc.serverfeatures.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.repairnpc.RepairNPC;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

public class EcoUtil {

    private static ConfigNode node(String key) {
        // Typed, MemorySection-free access
        return RepairNPC.getInstance().getConfigHandler().node(key);
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
        // Normalize material key to the same pattern used in config (lowercase, '-' instead of '_')
        String matKey = item.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');

        // Base price
        ConfigNode basePrices = node("base-prices");
        double baseDefault = basePrices.get("default").as(Double.class, 0.0);
        double basePrice   = basePrices.get(matKey).as(Double.class, baseDefault);

        // Price per durability point
        ConfigNode perPointCfg = node("price-per-durability-point");
        double ppDefault = perPointCfg.get("default").as(Double.class, 0.0);
        double perPoint  = perPointCfg.get(matKey).as(Double.class, ppDefault);

        double durabilityCost = 0.0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dmg) {
            // getDamage() is how much durability is missing
            durabilityCost = dmg.getDamage() * perPoint;
        }

        // Enchantment modifiers
        ConfigNode enchCfg = node("enchantment-modifiers");
        double enchDefault = enchCfg.get("default").as(Double.class, 0.0);

        double enchantmentCost = 0.0;
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            // Support both namespaced ("minecraft:sharpness") and bare ("sharpness")
            String bare = e.getKey().getKey().getKey().toLowerCase(Locale.ROOT).replace('_', '-');
            String namespaced = e.getKey().getKey().toString().toLowerCase(Locale.ROOT).replace('_', '-');

            double mod = enchCfg.get(namespaced).as(Double.class,
                    enchCfg.get(bare).as(Double.class, enchDefault));

            enchantmentCost += mod * e.getValue();
        }

        return basePrice + durabilityCost + enchantmentCost;
    }
}
