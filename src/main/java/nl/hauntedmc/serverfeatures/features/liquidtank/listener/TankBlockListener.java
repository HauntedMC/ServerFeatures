package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.ExperienceTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class TankBlockListener implements Listener {

    private final LiquidTank feature;

    public TankBlockListener(LiquidTank feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void placeOfLiquidTank(BlockPlaceEvent e) {
        if (e.isCancelled()) return;

        // Only consider hoppers placed with our legit item
        if (e.getBlock().getType() != Material.HOPPER) return;
        if (!ItemCreator.isLiquidTankItem(feature, e.getItemInHand())) return;

        try {
            if (e.getPlayer().hasPermission("serverfeatures.feature.liquidtank.use")) {
                if (e.getPlayer().hasPermission("serverfeatures.feature.liquidtank.limit.bypass")
                        || feature.getTankManager().canPlaceTank(e.getBlock().getLocation())) {
                    feature.getTankManager().createLiquidTank(e.getBlock().getLocation());

                    if (feature.getTankManager().isEnableItems()) {
                        this.addItems(e.getBlock());
                    }
                } else {
                    MessageUtils.sendActionbar(e.getPlayer(),
                            "&cYou can only place down " + feature.getTankManager().getMaxAmountPerChunk() + " per chunk!");
                    e.setCancelled(true);
                }
            } else {
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(BlockExplodeEvent e) {
        if (e.isCancelled()) return;

        ArrayList<AbstractTank> toRemove = new ArrayList<>();
        for (Block b : e.blockList()) {
            if (b.getType() != Material.HOPPER) continue;
            AbstractTank t = feature.getTankManager().getTank(b.getLocation());
            if (t != null) toRemove.add(t);
        }
        for (AbstractTank t : toRemove) {
            feature.getTankManager().removeTank(t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void breakOfLiquidTank(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        if (e.getBlock().getType() != Material.HOPPER) return;

        AbstractTank tank = feature.getTankManager().getTank(e.getBlock().getLocation());
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
            if (block.getType() == Material.HOPPER) {
                Hopper hopper = (Hopper) block.getState();
                hopper.getInventory().setItem(3, new ItemStack(Material.GLASS, 7));
                hopper.getInventory().setItem(4, new ItemStack(Material.COMPARATOR, 1));
            }
        }, BukkitTime.ticks(2L));
    }
}
