package nl.hauntedmc.serverfeatures.features.invtools.support;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Creates lightweight item doubles without invoking Paper's server-backed ItemStack factory.
 */
public final class TestItemStacks {

    private static final Map<Material, Material> TEST_MATERIALS = new ConcurrentHashMap<>();

    private TestItemStacks() {
    }

    public static ItemStack item(Material material) {
        return item(material, 1);
    }

    public static ItemStack item(Material material, int initialAmount) {
        return item(material, initialAmount, 64);
    }

    public static ItemStack item(Material material, int initialAmount, int maxStackSize) {
        Material testMaterial = TEST_MATERIALS.computeIfAbsent(material, ignored -> {
            Material mocked = mock(Material.class);
            when(mocked.isAir()).thenReturn(false);
            when(mocked.getEquipmentSlot()).thenReturn(testEquipmentSlot(ignored));
            return mocked;
        });
        return mockedItem(testMaterial, initialAmount, maxStackSize);
    }

    private static ItemStack mockedItem(Material testMaterial, int initialAmount, int maxStackSize) {
        AtomicInteger amount = new AtomicInteger(initialAmount);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(testMaterial);
        when(stack.getAmount()).thenAnswer(invocation -> amount.get());
        when(stack.getMaxStackSize()).thenReturn(maxStackSize);
        when(stack.clone()).thenAnswer(invocation -> mockedItem(testMaterial, amount.get(), maxStackSize));
        when(stack.isSimilar(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == testMaterial;
        });
        doAnswer(invocation -> {
            amount.set(invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(anyInt());
        return stack;
    }

    private static EquipmentSlot testEquipmentSlot(Material material) {
        return switch (material) {
            case DIAMOND_HELMET, CARVED_PUMPKIN -> EquipmentSlot.HEAD;
            case DIAMOND_CHESTPLATE, IRON_CHESTPLATE -> EquipmentSlot.CHEST;
            case DIAMOND_LEGGINGS -> EquipmentSlot.LEGS;
            case DIAMOND_BOOTS -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
