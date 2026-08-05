package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.ExperienceTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public final class TankBlockListener implements Listener {

    private final LiquidTank feature;

    public TankBlockListener(LiquidTank feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void validateLiquidTankPlacement(BlockPlaceEvent e) {
        // Only consider hoppers placed with our legit item
        if (e.getBlock().getType() != Material.HOPPER) return;
        if (!ItemCreator.isLiquidTankItem(feature, e.getItemInHand())) return;

        if (e.getPlayer().hasPermission("serverfeatures.feature.liquidtank.use")) {
            if (e.getPlayer().hasPermission("serverfeatures.feature.liquidtank.limit.bypass")
                    || feature.getTankManager().canPlaceTank(e.getBlock().getLocation())) {
                return;
            } else {
                MessageUtils.sendActionbar(e.getPlayer(),
                        "&cYou can only place down " + feature.getTankManager().getMaxAmountPerChunk() + " per chunk!");
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeLiquidTank(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER
                || !ItemCreator.isLiquidTankItem(feature, event.getItemInHand())) {
            return;
        }
        feature.getTankManager().createLiquidTank(block.getLocation());
        if (feature.getTankManager().isEnableItems()) {
            addItems(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        removeExplodedTanks(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        removeExplodedTanks(e.blockList());
    }

    private void removeExplodedTanks(Iterable<Block> blocks) {
        ArrayList<AbstractTank> toRemove = new ArrayList<>();
        for (Block b : blocks) {
            if (b.getType() != Material.HOPPER) continue;
            AbstractTank t = feature.getTankManager().getTank(b);
            if (t != null) toRemove.add(t);
        }
        for (AbstractTank t : toRemove) {
            feature.getTankManager().removeTank(t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakOfLiquidTank(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.HOPPER) return;

        AbstractTank tank = feature.getTankManager().getTank(e.getBlock());
        if (tank == null) return; // Not one of ours; let vanilla break proceed

        e.setCancelled(true);

        // Drop exp if needed (same behavior)
        if (!e.getPlayer().getGameMode().equals(GameMode.CREATIVE) && tank instanceof ExperienceTank) {
            int amount = tank.getQuantity();
            ExperienceOrb orb = tank.getLocation().getWorld()
                    .spawn(tank.getLocation().clone().add(0.5, 0.5, 0.5), ExperienceOrb.class);
            orb.setExperience(amount);
        }

        // Remove tank + clear hopper inv
        feature.getTankManager().removeTank(tank);
        Hopper hopper = (Hopper) e.getBlock().getState();
        hopper.getInventory().clear();

        // Drop our legit tank item back (survival only)
        if (!e.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            ItemStack drop = ItemCreator.createTankItem(feature, 1);
            e.getBlock().getWorld().dropItemNaturally(
                    e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5),
                    drop
            );
        }

        e.getBlock().setType(Material.AIR);
    }

    public void addItems(Block block) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (block.getType() == Material.HOPPER
                    && feature.getTankManager().getTank(block) != null) {
                Hopper hopper = (Hopper) block.getState();
                hopper.getInventory().setItem(3, new ItemStack(Material.GLASS, 7));
                hopper.getInventory().setItem(4, new ItemStack(Material.COMPARATOR, 1));
            }
        }, BukkitTime.ticks(2L));
    }
}
