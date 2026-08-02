package nl.hauntedmc.serverfeatures.features.autopickup.listener;

import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.transfer.AutoPickupTransferCommitter.AutoPickupCommitException;
import nl.hauntedmc.serverfeatures.features.autopickup.transfer.AutoPickupTransferPlanner;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class AutoPickupBlockDropListener implements Listener {

    private final AutoPickup feature;

    public AutoPickupBlockDropListener(AutoPickup feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!feature.preferences().isEnabled(player) || !feature.settings().allows(player)) {
            return;
        }

        List<Item> eligibleItems = new ArrayList<>();
        List<ItemStack> offeredStacks = new ArrayList<>();
        for (Item item : event.getItems()) {
            try {
                if (!feature.originClassifier().eligible(
                        event.getBlockState(),
                        item,
                        feature.settings().dropScope()
                )) {
                    continue;
                }
                ItemStack offered = AutoPickupTransferPlanner.cloneOrNull(item.getItemStack());
                if (offered == null) {
                    continue;
                }
                eligibleItems.add(item);
                offeredStacks.add(offered);
            } catch (RuntimeException exception) {
                // This item remains untouched on the ground; other valid drops may still be collected.
                feature.reportTransferFailure(player, exception);
            }
        }
        if (eligibleItems.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        AutoPickupTransferPlanner.TransferPlan plan;
        try {
            plan = feature.transferPlanner().plan(
                    inventory.getStorageContents(),
                    offeredStacks,
                    inventory.getMaxStackSize()
            );
        } catch (RuntimeException exception) {
            feature.reportTransferFailure(player, exception);
            return;
        }

        if (plan.totalInserted() > 0) {
            try {
                feature.transferCommitter().commit(inventory, event, eligibleItems, plan);
            } catch (AutoPickupCommitException exception) {
                feature.reportTransferFailure(player, exception);
                if (exception.rollbackFailed()) {
                    feature.preferences().disableForSession(player);
                    player.sendMessage(feature.getLocalizationHandler()
                            .getMessage("autopickup.session_disabled")
                            .forAudience(player)
                            .build());
                }
                return;
            }
            feature.playPickupSound(player);
        }

        if (plan.totalRemaining() > 0) {
            boolean partial = plan.totalInserted() > 0;
            long now = System.nanoTime();
            if (feature.preferences().shouldNotifyFull(player, partial, now)) {
                feature.notifyInventoryFull(player, plan.totalRemaining(), plan.remainingStacks());
            }
        }
    }
}
