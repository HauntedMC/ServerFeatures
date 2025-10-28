package nl.hauntedmc.serverfeatures.features.parcour.internal;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

/**
 * In-memory snapshot of a player's inventory & vital stats.
 * Not persisted; only used for active sessions.
 */
public final class ParcourInventorySnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offHand;

    private final int level;
    private final float exp;

    private final double health;
    private final int food;
    private final float saturation;

    private ParcourInventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                                     int level, float exp, double health, int food, float saturation) {
        this.contents = contents;
        this.armor = armor;
        this.offHand = offHand;
        this.level = level;
        this.exp = exp;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    public static ParcourInventorySnapshot capture(Player p) {
        // Deep-ish copies of arrays; ItemStack is Cloneable
        ItemStack[] inv = Arrays.stream(p.getInventory().getContents())
                .map(is -> is == null ? null : is.clone())
                .toArray(ItemStack[]::new);

        ItemStack[] arm = Arrays.stream(p.getInventory().getArmorContents())
                .map(is -> is == null ? null : is.clone())
                .toArray(ItemStack[]::new);

        ItemStack off = p.getInventory().getItemInOffHand();
        off = (off == null ? null : off.clone());

        int level = p.getLevel();
        float exp = p.getExp();

        double hp = p.getHealth();
        int food = p.getFoodLevel();
        float sat = p.getSaturation();

        return new ParcourInventorySnapshot(inv, arm, off, level, exp, hp, food, sat);
    }

    public void restore(Player p) {
        // Restore inventory
        p.getInventory().setContents(cloneArray(contents));
        p.getInventory().setArmorContents(cloneArray(armor));
        p.getInventory().setItemInOffHand(offHand == null ? null : offHand.clone());
        p.updateInventory();

        // Restore xp
        p.setLevel(level);
        p.setExp(exp);

        // Restore food/saturation
        p.setFoodLevel(food);
        p.setSaturation(saturation);

        // Restore health safely under current max
        double max = p.getAttribute(Attribute.MAX_HEALTH) != null
                ? Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()
                : p.getMaxHealth();
        p.setHealth(Math.max(0.1, Math.min(health, max))); // avoid 0
    }

    private static ItemStack[] cloneArray(ItemStack[] arr) {
        if (arr == null) return null;
        ItemStack[] out = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i] == null ? null : arr[i].clone();
        }
        return out;
    }
}
