package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
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
import org.bukkit.inventory.InventoryHolder;

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

    @EventHandler
    public void InventoryPickupItemEvent(InventoryPickupItemEvent inventoryPickupItemEvent) {
        if (inventoryPickupItemEvent.getInventory().getHolder() instanceof Hopper && feature.getTankManager().getTank(((Hopper) inventoryPickupItemEvent.getInventory().getHolder()).getLocation()) != null) {
            inventoryPickupItemEvent.setCancelled(true);
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



    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryMoveItemEvent(InventoryMoveItemEvent inventoryMoveItemEvent) {
        AbstractTank liquidTank;
        Hopper hopper;
        InventoryHolder inventoryHolder;
        if (inventoryMoveItemEvent.getSource().getType().equals(InventoryType.HOPPER) && (inventoryHolder = inventoryMoveItemEvent.getSource().getHolder()) != null
                && inventoryHolder instanceof Hopper) {
            hopper = (Hopper) inventoryHolder;
            liquidTank = feature.getTankManager().getTank(hopper.getLocation());
            if (!hopper.getBlock().isBlockIndirectlyPowered() && !hopper.getBlock().isBlockPowered() && liquidTank != null) {
                InventoryHolder inventoryHolder2;
                if (!feature.getTankManager().isEnableItems()) {
                    hopper.getInventory().clear();
                }
                inventoryMoveItemEvent.setCancelled(true);
                if (inventoryMoveItemEvent.getDestination().getType().equals(InventoryType.HOPPER)
                        && (inventoryHolder2 = inventoryMoveItemEvent.getDestination().getHolder()) != null && inventoryHolder2 instanceof Hopper hopper2) {
                    AbstractTank liquidTank2 = feature.getTankManager().getTank(hopper2.getLocation());
                    if (!hopper2.getBlock().isBlockIndirectlyPowered() && !hopper2.getBlock().isBlockPowered() && liquidTank2 != null) {
                        if (!feature.getTankManager().isEnableItems()) {
                            hopper2.getInventory().clear();
                        }
                        if (!(liquidTank2.isOnCooldown() || liquidTank.isOnCooldown() || liquidTank.getTankType().equals(TankType.EMPTY))) {
                            if (liquidTank.getTankType().equals(liquidTank2.getTankType())) {
                                if (liquidTank2.getQuantity() < liquidTank2.getMaxQuantity()) {
                                    if (liquidTank.getQuantity() == liquidTank.getMaxQuantity() - liquidTank2.getQuantity()) {
                                        AbstractTank liquidTank3 = feature.getTankManager().emptyTank(liquidTank);
                                        liquidTank3.setOnCooldown();
                                        liquidTank2.setQuantity(liquidTank2.getMaxQuantity());
                                        liquidTank2.setOnCooldown();
                                    } else if (liquidTank.getQuantity() < liquidTank.getMaxQuantity() - liquidTank2.getQuantity()) {
                                        liquidTank2.setQuantity(liquidTank2.getQuantity() + liquidTank.getQuantity());
                                        AbstractTank liquidTank4 = feature.getTankManager().emptyTank(liquidTank);
                                        liquidTank4.setOnCooldown();
                                        liquidTank2.setOnCooldown();
                                    } else {
                                        liquidTank.setQuantity(liquidTank.getQuantity() - (liquidTank.getMaxQuantity() - liquidTank2.getQuantity()));
                                        liquidTank2.setQuantity(liquidTank2.getMaxQuantity());
                                        liquidTank2.setOnCooldown();
                                        liquidTank.updateVisuals();
                                    }
                                    liquidTank2.updateVisuals();
                                }
                            } else if (liquidTank2.getTankType().equals(TankType.EMPTY)) {
                                if (liquidTank.isOverFlown()) {
                                    AbstractTank liquidTank5 = feature.getTankManager().changeTankType(liquidTank2, liquidTank.getTankType(), liquidTank.getMaxQuantity());
                                    liquidTank5.setOnCooldown();
                                    liquidTank.setQuantity(liquidTank.getQuantity() - liquidTank.getMaxQuantity());
                                    liquidTank.setOnCooldown();
                                } else {
                                    AbstractTank liquidTank6 = feature.getTankManager().changeTankType(liquidTank2, liquidTank.getTankType(), liquidTank.getQuantity());
                                    liquidTank6.setOnCooldown();
                                    AbstractTank liquidTank7 = feature.getTankManager().emptyTank(liquidTank);
                                    liquidTank7.setOnCooldown();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (inventoryMoveItemEvent.getDestination().getType().equals(InventoryType.HOPPER)
                && (inventoryHolder = inventoryMoveItemEvent.getDestination().getHolder()) != null && inventoryHolder instanceof Hopper
                && (feature.getTankManager().getTank((hopper = (Hopper) inventoryHolder).getLocation())) != null) {
            inventoryMoveItemEvent.setCancelled(true);
            if (!feature.getTankManager().isEnableItems()) {
                hopper.getInventory().clear();
            }
        }
    }

}
