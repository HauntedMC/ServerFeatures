package nl.hauntedmc.serverfeatures.features.graveyard.claim;

import nl.hauntedmc.serverfeatures.features.graveyard.capture.InventorySlot;
import nl.hauntedmc.serverfeatures.features.graveyard.capture.PlayerInventoryState;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveItemEntry;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic inventory transfer planning. It never calls Bukkit inventory insertion methods,
 * because those methods may drop overflow according to vanilla behaviour.
 */
public final class GraveClaimPlanner {
    private final GravePayloadCodec codec;
    private final boolean partialClaims;

    public GraveClaimPlanner(GravePayloadCodec codec, boolean partialClaims) {
        this.codec = codec;
        this.partialClaims = partialClaims;
    }

    public ClaimTransferPlan plan(PlayerInventoryState current, GravePayload payload) throws IOException {
        PlayerInventoryState result = current.copy();
        List<GraveItemEntry> remainingEntries = new ArrayList<>();
        int transferredEntries = 0;

        for (GraveItemEntry entry : payload.entries()) {
            ItemStack original = codec.decodeItem(entry);
            ItemStack remainder = original.clone();
            int before = remainder.getAmount();

            mergeIntoSlot(result, entry.preferredSlot(), remainder);
            mergeExistingStorage(result, remainder);
            fillEmptyStorage(result, remainder);

            int transferred = before - remainder.getAmount();
            if (transferred > 0) {
                transferredEntries++;
            }
            if (remainder.getAmount() > 0) {
                remainingEntries.add(codec.createEntry(entry.entryId(), entry.preferredSlot(), remainder));
            }
        }

        int transferredExperience = payload.remainingExperience();
        GravePayload remainingPayload = payload.next(remainingEntries, 0);
        boolean changed = transferredEntries > 0 || transferredExperience > 0;

        if (!partialClaims && !remainingEntries.isEmpty()) {
            return new ClaimTransferPlan(
                    current.copy(),
                    payload,
                    0,
                    0,
                    false
            );
        }
        return new ClaimTransferPlan(
                result,
                remainingPayload,
                transferredEntries,
                transferredExperience,
                changed
        );
    }

    private static void mergeIntoSlot(PlayerInventoryState state, int slot, ItemStack remainder) {
        if (remainder.getAmount() <= 0 || !isLegalPreferredSlot(slot, remainder)) {
            return;
        }
        ItemStack current = state.get(slot);
        if (current == null) {
            state.set(slot, remainder);
            remainder.setAmount(0);
            return;
        }
        merge(current, remainder);
        state.set(slot, current);
    }

    private static void mergeExistingStorage(PlayerInventoryState state, ItemStack remainder) {
        if (remainder.getAmount() <= 0) {
            return;
        }
        for (int slot = 0; slot < 36 && remainder.getAmount() > 0; slot++) {
            ItemStack current = state.get(slot);
            if (current == null || !current.isSimilar(remainder)) {
                continue;
            }
            merge(current, remainder);
            state.set(slot, current);
        }
    }

    private static void fillEmptyStorage(PlayerInventoryState state, ItemStack remainder) {
        while (remainder.getAmount() > 0) {
            int empty = state.firstEmptyStorageSlot();
            if (empty < 0) {
                return;
            }
            int amount = Math.min(remainder.getAmount(), remainder.getMaxStackSize());
            ItemStack inserted = remainder.clone();
            inserted.setAmount(amount);
            state.set(empty, inserted);
            remainder.setAmount(remainder.getAmount() - amount);
        }
    }

    private static void merge(ItemStack target, ItemStack remainder) {
        if (!target.isSimilar(remainder)) {
            return;
        }
        int capacity = Math.max(0, target.getMaxStackSize() - target.getAmount());
        int moved = Math.min(capacity, remainder.getAmount());
        target.setAmount(target.getAmount() + moved);
        remainder.setAmount(remainder.getAmount() - moved);
    }

    private static boolean isLegalPreferredSlot(int slot, ItemStack item) {
        if (InventorySlot.isStorage(slot) || slot == InventorySlot.OFF_HAND) {
            return true;
        }
        EquipmentSlot required = switch (slot) {
            case InventorySlot.BOOTS -> EquipmentSlot.FEET;
            case InventorySlot.LEGGINGS -> EquipmentSlot.LEGS;
            case InventorySlot.CHESTPLATE -> EquipmentSlot.CHEST;
            case InventorySlot.HELMET -> EquipmentSlot.HEAD;
            default -> null;
        };
        return required != null && item.getType().getEquipmentSlot() == required;
    }
}
