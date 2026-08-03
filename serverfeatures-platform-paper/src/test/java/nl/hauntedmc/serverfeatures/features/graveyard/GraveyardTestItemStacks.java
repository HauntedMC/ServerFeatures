package nl.hauntedmc.serverfeatures.features.graveyard;

import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public final class GraveyardTestItemStacks {
    private GraveyardTestItemStacks() {
    }

    public static ItemStack stack(Material material, int amount) {
        AtomicInteger currentAmount = new AtomicInteger(amount);
        ItemStack item = mock(ItemStack.class, withSettings().lenient());
        doAnswer(ignored -> material).when(item).getType();
        doAnswer(ignored -> currentAmount.get()).when(item).getAmount();
        doAnswer(invocation -> {
            currentAmount.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(anyInt());
        doAnswer(ignored -> maximumStackSize(material)).when(item).getMaxStackSize();
        doAnswer(ignored -> stack(material, currentAmount.get())).when(item).clone();
        doAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == material;
        }).when(item).isSimilar(any(ItemStack.class));
        doAnswer(ignored -> encode(material, currentAmount.get())).when(item).serializeAsBytes();
        return item;
    }

    public static GravePayloadCodec.ItemStackBinaryCodec binaryCodec() {
        return new GravePayloadCodec.ItemStackBinaryCodec() {
            @Override
            public byte[] serialize(ItemStack item) {
                return encode(item.getType(), item.getAmount());
            }

            @Override
            public ItemStack deserialize(byte[] bytes) {
                return decode(bytes);
            }
        };
    }

    private static byte[] encode(Material material, int amount) {
        byte[] name = material.name().getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + name.length + Integer.BYTES);
        buffer.putInt(name.length);
        buffer.put(name);
        buffer.putInt(amount);
        return buffer.array();
    }

    private static ItemStack decode(byte[] encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        int nameLength = buffer.getInt();
        byte[] name = new byte[nameLength];
        buffer.get(name);
        Material material = Material.valueOf(new String(name, StandardCharsets.US_ASCII));
        int amount = buffer.getInt();
        return stack(material, amount);
    }

    private static int maximumStackSize(Material material) {
        return switch (material) {
            case DIAMOND_SWORD -> 1;
            default -> 64;
        };
    }
}
