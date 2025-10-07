package nl.hauntedmc.serverfeatures.features.bettercoral.recipe;

import nl.hauntedmc.serverfeatures.features.bettercoral.BetterCoral;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CoralRecipes {

    private final BetterCoral feature;
    private final int cookTime;   // ticks
    private final float exp;

    private final Map<Material, Material> liveToDead = new EnumMap<>(Material.class);
    private final List<NamespacedKey> keys = new ArrayList<>();

    public CoralRecipes(BetterCoral feature) {
        this.feature = feature;
        this.cookTime = feature.getConfigHandler().node("furnace").get("cook_time_ticks").as(Integer.class, 20);
        this.exp = feature.getConfigHandler().node("furnace").get("experience").as(Float.class, 0.0F);

        put(Material.TUBE_CORAL_BLOCK,  Material.DEAD_TUBE_CORAL_BLOCK);
        put(Material.BRAIN_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK);
        put(Material.BUBBLE_CORAL_BLOCK,Material.DEAD_BUBBLE_CORAL_BLOCK);
        put(Material.FIRE_CORAL_BLOCK,  Material.DEAD_FIRE_CORAL_BLOCK);
        put(Material.HORN_CORAL_BLOCK,  Material.DEAD_HORN_CORAL_BLOCK);

        put(Material.TUBE_CORAL,  Material.DEAD_TUBE_CORAL);
        put(Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL);
        put(Material.BUBBLE_CORAL,Material.DEAD_BUBBLE_CORAL);
        put(Material.FIRE_CORAL,  Material.DEAD_FIRE_CORAL);
        put(Material.HORN_CORAL,  Material.DEAD_HORN_CORAL);

        put(Material.TUBE_CORAL_FAN,  Material.DEAD_TUBE_CORAL_FAN);
        put(Material.BRAIN_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        put(Material.BUBBLE_CORAL_FAN,Material.DEAD_BUBBLE_CORAL_FAN);
        put(Material.FIRE_CORAL_FAN,  Material.DEAD_FIRE_CORAL_FAN);
        put(Material.HORN_CORAL_FAN,  Material.DEAD_HORN_CORAL_FAN);

        put(Material.TUBE_CORAL_WALL_FAN,  Material.DEAD_TUBE_CORAL_WALL_FAN);
        put(Material.BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_WALL_FAN);
        put(Material.BUBBLE_CORAL_WALL_FAN,Material.DEAD_BUBBLE_CORAL_WALL_FAN);
        put(Material.FIRE_CORAL_WALL_FAN,  Material.DEAD_FIRE_CORAL_WALL_FAN);
        put(Material.HORN_CORAL_WALL_FAN,  Material.DEAD_HORN_CORAL_WALL_FAN);
    }

    private void put(Material live, Material dead) {
        liveToDead.put(live, dead);
    }

    public void registerAll() {
        for (Map.Entry<Material, Material> e : liveToDead.entrySet()) {
            Material in = e.getKey();
            Material out = e.getValue();

            NamespacedKey key = new NamespacedKey(feature.getPlugin(),
                    "coral_burn_" + in.name().toLowerCase());
            FurnaceRecipe r = new FurnaceRecipe(
                    key,
                    new org.bukkit.inventory.ItemStack(out),
                    new RecipeChoice.MaterialChoice(in),
                    exp,
                    cookTime
            );
            feature.getPlugin().getServer().addRecipe(r);
            keys.add(key);
        }
    }

    public void unregisterAll() {
        var server = feature.getPlugin().getServer();
        for (NamespacedKey k : keys) {
            server.removeRecipe(k);
        }
        keys.clear();
    }
}
