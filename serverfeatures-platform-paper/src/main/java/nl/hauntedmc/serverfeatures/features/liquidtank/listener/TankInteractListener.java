package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.LiquidTankManager;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

public final class TankInteractListener implements Listener {

    private final LiquidTank feature;

    public TankInteractListener(LiquidTank feature) {
        this.feature = feature;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void rightClickOnLiquidTank(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.HOPPER) {
            return;
        }
        Player player = event.getPlayer();
        GameMode gameMode = player.getGameMode();
        if (gameMode != GameMode.SURVIVAL
                && gameMode != GameMode.ADVENTURE
                && gameMode != GameMode.CREATIVE) {
            return;
        }
        AbstractTank tank = feature.getTankManager().getTank(clicked);
        if (tank == null) {
            return;
        }
        event.setCancelled(true);
        if (feature.getTankManager().isPermissionRequired()
                && !player.hasPermission("serverfeatures.feature.liquidtank.use")) {
            return;
        }
        tank.onInteract(player);
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
        LiquidTankManager manager = feature.getTankManager();
        Location sourceLocation = hopperLocation(src);
        Location destinationLocation = hopperLocation(dst);
        AbstractTank source = sourceLocation == null ? null : manager.getTank(sourceLocation);
        AbstractTank destination = destinationLocation == null ? null : manager.getTank(destinationLocation);

        // A tank never accepts vanilla hopper items, powered or not.
        if (destination != null) {
            e.setCancelled(true);
            if (!manager.isEnableItems()) {
                dst.clear();
            }
        }

        if (source == null || isPowered(sourceLocation.getBlock())) {
            return;
        }

        e.setCancelled(true);
        if (!manager.isEnableItems()) {
            src.clear();
        }
        if (destination == null || isPowered(destinationLocation.getBlock())) {
            return;
        }
        transfer(manager, source, destination);
    }

    static void transfer(
            LiquidTankManager manager,
            AbstractTank source,
            AbstractTank destination
    ) {
        if (destination.isOnCooldown()
                || source.isOnCooldown()
                || source.getTankType() == TankType.EMPTY) {
            return;
        }
        int sourceQuantity = source.getQuantity();
        if (sourceQuantity <= 0) {
            return;
        }

        if (source.getTankType() == destination.getTankType()) {
            int space = destination.getMaxQuantity() - destination.getQuantity();
            if (space <= 0) {
                return;
            }
            int transferred = Math.min(sourceQuantity, space);
            destination.setQuantity(destination.getQuantity() + transferred);
            destination.setOnCooldown();
            destination.updateVisuals();

            if (transferred == sourceQuantity) {
                manager.emptyTank(source).setOnCooldown();
            } else {
                source.setQuantity(sourceQuantity - transferred);
                source.updateVisuals();
            }
            return;
        }

        if (destination.getTankType() != TankType.EMPTY) {
            return;
        }

        int transferred = Math.min(sourceQuantity, source.getMaxQuantity());
        manager.changeTankType(destination, source.getTankType(), transferred).setOnCooldown();
        if (transferred == sourceQuantity) {
            manager.emptyTank(source).setOnCooldown();
        } else {
            source.setQuantity(sourceQuantity - transferred);
            source.setOnCooldown();
            source.updateVisuals();
        }
    }

    private static Location hopperLocation(Inventory inventory) {
        return inventory.getType() == InventoryType.HOPPER ? inventory.getLocation() : null;
    }

    private static boolean isPowered(Block b) {
        return b.isBlockPowered() || b.isBlockIndirectlyPowered();
    }


}
