package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public class TankInteractListener implements Listener {

    private final LiquidTank feature;

    public TankInteractListener(LiquidTank feature) {
        this.feature = feature;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rightClickOnLiquidTank(PlayerInteractEvent playerInteractEvent) {
        Player player = playerInteractEvent.getPlayer();
        if (!(player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode().equals(GameMode.ADVENTURE) || player.getGameMode().equals(GameMode.CREATIVE) || playerInteractEvent.getAction().equals(Action.RIGHT_CLICK_BLOCK) && Objects.requireNonNull(playerInteractEvent.getClickedBlock()).getType() == Material.HOPPER)) {
            return;
        }
        if (player.isSneaking()) {
            if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                playerInteractEvent.setCancelled(true);
            }
        }
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
            try {
                AbstractTank liquidTank = feature.getTankManager().getTank(Objects.requireNonNull(playerInteractEvent.getClickedBlock()).getLocation());
                if (liquidTank != null) {
                    playerInteractEvent.setCancelled(true);
                    if (!player.hasPermission("serverfeatures.feature.liquidtank.use") && (boolean) feature.getConfigHandler().getSetting("enable-permission")) {
                        return;
                    }
                    feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> liquidTank.onInteract(player));
                }
            } catch (Exception exception) {
                // empty catch block
            }
        });

    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onLiquidTankOpen(InventoryOpenEvent e) {
        final Inventory inv = e.getInventory();

        // Only care about block hoppers; skip hopper minecarts, etc.
        if (inv.getType() != InventoryType.HOPPER) return;

        // Cheap path: goes via the tile entity; no BlockState creation
        final Location loc = inv.getLocation();
        if (loc == null) return;

        if (feature.getTankManager().getTank(loc) != null) {
            e.setCancelled(true);
        }

    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onInventoryPickupItem(InventoryPickupItemEvent e) {
        final Inventory inv = e.getInventory();

        // Only care about block hoppers; skip hopper minecarts, etc.
        if (inv.getType() != InventoryType.HOPPER) return;

        // Cheap path: goes via the tile entity; no BlockState creation
        final Location loc = inv.getLocation();
        if (loc == null) return;

        if (feature.getTankManager().getTank(loc) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        final Inventory src = e.getSource();
        final Inventory dst = e.getDestination();

        // --- source hopper is a tank ---
        if (src.getType() == InventoryType.HOPPER) {
            Location sLoc = src.getLocation(); // null for non-block holders (e.g., hopper minecarts)
            if (sLoc != null) {
                sLoc = sLoc.toBlockLocation();
                AbstractTank tank = feature.getTankManager().getTank(sLoc);
                if (tank != null && !isPowered(sLoc.getBlock())) {
                    if (!feature.getTankManager().isEnableItems()) src.clear();
                    e.setCancelled(true);

                    // If destination is also a tank hopper, run tank<->tank transfer
                    if (dst.getType() == InventoryType.HOPPER) {
                        Location dLoc = dst.getLocation();
                        if (dLoc != null) {
                            dLoc = dLoc.toBlockLocation();
                            AbstractTank tank2 = feature.getTankManager().getTank(dLoc);
                            if (tank2 != null && !isPowered(dLoc.getBlock())) {
                                if (!feature.getTankManager().isEnableItems()) dst.clear();

                                if (!(tank2.isOnCooldown() || tank.isOnCooldown() || tank.getTankType() == TankType.EMPTY)) {
                                    if (tank.getTankType() == tank2.getTankType()) {
                                        if (tank2.getQuantity() < tank2.getMaxQuantity()) {
                                            int space = tank2.getMaxQuantity() - tank2.getQuantity();
                                            if (tank.getQuantity() == space) {
                                                AbstractTank t3 = feature.getTankManager().emptyTank(tank);
                                                t3.setOnCooldown();
                                                tank2.setQuantity(tank2.getMaxQuantity());
                                                tank2.setOnCooldown();
                                            } else if (tank.getQuantity() < space) {
                                                tank2.setQuantity(tank2.getQuantity() + tank.getQuantity());
                                                AbstractTank t4 = feature.getTankManager().emptyTank(tank);
                                                t4.setOnCooldown();
                                                tank2.setOnCooldown();
                                            } else {
                                                tank.setQuantity(tank.getQuantity() - space);
                                                tank2.setQuantity(tank2.getMaxQuantity());
                                                tank2.setOnCooldown();
                                                tank.updateVisuals();
                                            }
                                            tank2.updateVisuals();
                                        }
                                    } else if (tank2.getTankType() == TankType.EMPTY) {
                                        if (tank.isOverFlown()) {
                                            AbstractTank t5 = feature.getTankManager().changeTankType(tank2, tank.getTankType(), tank.getMaxQuantity());
                                            t5.setOnCooldown();
                                            tank.setQuantity(tank.getQuantity() - tank.getMaxQuantity());
                                            tank.setOnCooldown();
                                        } else {
                                            AbstractTank t6 = feature.getTankManager().changeTankType(tank2, tank.getTankType(), tank.getQuantity());
                                            t6.setOnCooldown();
                                            AbstractTank t7 = feature.getTankManager().emptyTank(tank);
                                            t7.setOnCooldown();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- destination hopper is a tank (mirror guard) ---
        if (dst.getType() == InventoryType.HOPPER) {
            Location dLoc = dst.getLocation();
            if (dLoc != null && feature.getTankManager().getTank(dLoc.toBlockLocation()) != null) {
                e.setCancelled(true);
                if (!feature.getTankManager().isEnableItems()) dst.clear();
            }
        }
    }

    private static boolean isPowered(Block b) {
        return b.isBlockPowered() || b.isBlockIndirectlyPowered();
    }


}
