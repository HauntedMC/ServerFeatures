package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.ExperienceTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Objects;

public class LiquidTankListener implements Listener {

    private final LiquidTank feature;

    public LiquidTankListener(LiquidTank feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void placeOfLiquidTank(BlockPlaceEvent blockPlaceEvent) {
        if (blockPlaceEvent.isCancelled()) {
            return;
        }
        try {
            ItemMeta meta = blockPlaceEvent.getItemInHand().getItemMeta();
            Component display = (meta != null) ? meta.displayName() : null;

            if (blockPlaceEvent.getBlock().getType() != Material.HOPPER
                    || display == null
                    || !LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(feature.getTankManager().getItemName())
                    .equals(display)) {
                return;
            }
            if (blockPlaceEvent.getPlayer().hasPermission("serverfeatures.feature.liquidtank.use") || !(boolean)feature.getConfigHandler().getSetting("enable-permission")) {
                if (blockPlaceEvent.getPlayer().hasPermission("serverfeatures.feature.liquidtank.limit.bypass") || feature.getTankManager().canPlaceTank(blockPlaceEvent.getBlock().getLocation())) {
                    feature.getTankManager().createLiquidTank(blockPlaceEvent.getBlock().getLocation());

                    if (feature.getTankManager().isEnableItems()) {
                        this.addItems(blockPlaceEvent.getBlock());
                    }
                } else {
                    MessageUtils.sendTitle(blockPlaceEvent.getPlayer(),
                            "&cYou can only place down " + feature.getTankManager().getMaxAmountPerChunk() + " per chunk!");
                    blockPlaceEvent.setCancelled(true);
                }
            } else {
                blockPlaceEvent.setCancelled(true);
            }
        } catch (Exception exception) {
            blockPlaceEvent.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(BlockExplodeEvent blockExplodeEvent) {
        if (blockExplodeEvent.isCancelled()) {
            return;
        }
        ArrayList<AbstractTank> arrayList = new ArrayList<>();
        for (Block object : blockExplodeEvent.blockList()) {
            AbstractTank liquidTank;
            if (object.getType() != Material.HOPPER || (liquidTank = feature.getTankManager().getTank(object.getLocation())) == null)
                continue;
            arrayList.add(liquidTank);
        }
        for (AbstractTank liquidTank : arrayList) {
            feature.getTankManager().removeTank(liquidTank);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void breakOfLiquidTank(BlockBreakEvent blockBreakEvent) {
        if (blockBreakEvent.isCancelled()) {
            return;
        }
        if (blockBreakEvent.getBlock().getType() != Material.HOPPER) {
            return;
        }
        AbstractTank liquidTank = feature.getTankManager().getTank(blockBreakEvent.getBlock().getLocation());
        if (liquidTank != null) {
            blockBreakEvent.setCancelled(true);
            if (!blockBreakEvent.getPlayer().getGameMode().equals(GameMode.CREATIVE) && liquidTank instanceof ExperienceTank) {
                int n = liquidTank.getQuantity();
                ExperienceOrb experienceOrb = liquidTank.getLocation().getWorld().spawn(liquidTank.getLocation().clone().add(0.5, 0.5, 0.5), ExperienceOrb.class);
                experienceOrb.setExperience(n);
            }
            feature.getTankManager().removeTank(liquidTank);
            Hopper hopper = (Hopper) blockBreakEvent.getBlock().getState();
            hopper.getInventory().clear();
            if (!blockBreakEvent.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                blockBreakEvent.getBlock().getWorld().dropItemNaturally(blockBreakEvent.getBlock().getLocation().clone().add(0.5, 0.5, 0.5),
                        ItemCreator.newItem(Material.HOPPER, 1, feature.getTankManager().getItemName(), ""));
            }
            blockBreakEvent.getBlock().setType(Material.AIR);
        }
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
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() ->  {
            try {
                AbstractTank liquidTank = feature.getTankManager().getTank(Objects.requireNonNull(playerInteractEvent.getClickedBlock()).getLocation());
                if (liquidTank != null) {
                    playerInteractEvent.setCancelled(true);
                    if (!player.hasPermission("serverfeatures.feature.liquidtank.use") && (boolean) feature.getConfigHandler().getSetting("enable-permission")) {
                        return;
                    }
                    feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() ->  liquidTank.onInteract(player));
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

    @EventHandler
    public void onTeleport(PlayerTeleportEvent playerTeleportEvent) {
        try {
            feature.getTankManager().loadUnloadedTankList(playerTeleportEvent.getTo().getWorld());
        } catch (Exception exception) {
            // empty catch block
        }
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            liquidTank.updatePlayerView();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            feature.getTankManager().loadUnloadedTankList(playerJoinEvent.getPlayer().getWorld());
            for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
                liquidTank.updatePlayerView(playerJoinEvent.getPlayer());
            }
        }, BukkitTime.ticks(0L));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent worldLoadEvent) {
        feature.getTankManager().loadUnloadedTankList(worldLoadEvent.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent worldUnloadEvent) {
        ArrayList<AbstractTank> arrayList = new ArrayList<>();
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            if (liquidTank.getLocation().getWorld() != worldUnloadEvent.getWorld())
                continue;
            arrayList.add(liquidTank);
            feature.getTankManager().getUnloadedTankList().add(new UnloadedTank(worldUnloadEvent.getWorld().getName(), liquidTank.getLocation().getBlockX(), liquidTank.getLocation().getBlockY(),
                    liquidTank.getLocation().getBlockZ(), liquidTank.getTankType(), liquidTank.getQuantity()));
        }
        for (AbstractTank liquidTank : arrayList) {
            feature.getTankManager().removeTank(liquidTank);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        for (AbstractTank liquidTank : feature.getTankManager().getTankList()) {
            liquidTank.updatePlayerView(playerMoveEvent.getPlayer());
        }
    }

    @EventHandler
    public void onLiquidTankOpen(InventoryOpenEvent inventoryOpenEvent) {
        if (inventoryOpenEvent.getInventory().getHolder() instanceof Hopper && feature.getTankManager().getTank(((Hopper) inventoryOpenEvent.getInventory().getHolder()).getLocation()) != null) {
            inventoryOpenEvent.setCancelled(true);
        }
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
