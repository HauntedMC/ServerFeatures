package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.*;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

public class LiquidTankManager implements Listener {
    public static File tanksDatabase;
    public static FileConfiguration tanks;
    List<AbstractTank> tankList = new ArrayList<>();
    List<UnloadedTank> unloadedTankList = new ArrayList<>();
    LiquidTank feature;
    private int maxAmountPerChunk;
    private String itemName;
    private boolean enableItems;

    public LiquidTankManager(LiquidTank feature) {
        this.feature = feature;
        AbstractTank.gameLoop(feature);
        EmptyTank.gameLoop(feature);
        LavaTank.gameLoop(feature);
        WaterTank.gameLoop(feature);
        MilkTank.gameLoop(feature);
        MushroomStewTank.gameLoop(feature);
        FoodTank.gameLoop(feature);
        ExperienceTank.gameLoop(feature);
        HoneyTank.gameLoop(feature);
        DragonBreathTank.gameLoop(feature);
        readConfigOptions();
    }

    private void readConfigOptions() {
        maxAmountPerChunk = (int) feature.getConfigHandler().getSetting("amount-per-chunk");
        itemName = ((String) feature.getConfigHandler().getSetting("item-name")).replace("&", "§");
        enableItems = (boolean) feature.getConfigHandler().getSetting("enable-items");
    }

    public List<AbstractTank> getTankList() {
        return this.tankList;
    }

    public void createLiquidTank(Location location) {
        EmptyTank emptyTank = new EmptyTank(location, feature);
        this.tankList.add(emptyTank);
        this.quickSave(true, false);
    }

    public void createLiquidTank(Location location, TankType tankType, int n) {
        AbstractTank liquidTank = tankType.equals(TankType.LAVA) ? new LavaTank(location, n, feature)
                : (tankType.equals(TankType.WATER) ? new WaterTank(location, n, feature)
                : (tankType.equals(TankType.MILK) ? new MilkTank(location, n, feature)
                : (tankType.equals(TankType.MUSHROOM_STEW) ? new MushroomStewTank(location, n, feature)
                : (tankType.equals(TankType.RABBIT_STEW) ? new RabbitStewTank(location, n, feature)
                : (tankType.equals(TankType.BEETROOT_SOUP) ? new BeetrootSoupTank(location, n, feature)
                : (tankType.equals(TankType.DRAGON_BREATH) ? new DragonBreathTank(location, n, feature)
                : (tankType.equals(TankType.EXPERIENCE) ? new ExperienceTank(location, n, feature)
                : (tankType.equals(TankType.HONEY) ? new HoneyTank(location, n, feature) : new EmptyTank(location, feature)))))))));
        this.tankList.add(liquidTank);
    }

    public AbstractTank changeTankType(AbstractTank liquidTank, TankType tankType, int n) {
        liquidTank.clear(false);
        AbstractTank liquidTank2 = tankType.equals(TankType.LAVA) ? new LavaTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.WATER) ? new WaterTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.MILK) ? new MilkTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.MUSHROOM_STEW) ? new MushroomStewTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.RABBIT_STEW) ? new RabbitStewTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.BEETROOT_SOUP) ? new BeetrootSoupTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.DRAGON_BREATH) ? new DragonBreathTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.EXPERIENCE) ? new ExperienceTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.HONEY) ? new HoneyTank(liquidTank.getLocation(), n, feature)
                : new EmptyTank(liquidTank.getLocation(), feature)))))))));
        this.tankList.remove(liquidTank);
        this.tankList.add(liquidTank2);
        return liquidTank2;
    }

    public AbstractTank emptyTank(AbstractTank liquidTank) {
        liquidTank.clear(false);
        this.tankList.remove(liquidTank);
        EmptyTank emptyTank = new EmptyTank(liquidTank.getLocation(), feature);
        this.tankList.add(emptyTank);
        return emptyTank;
    }

    public void removeTank(AbstractTank liquidTank) {
        liquidTank.clear(true);
        this.tankList.remove(liquidTank);
    }

    public void load() {
        try {
            if (!feature.getPlugin().getDataFolder().exists()) {
                feature.getPlugin().getDataFolder().mkdirs();
            }
            if (!(tanksDatabase = new File(feature.getPlugin().getDataFolder(), "tanks.yml")).exists()) {
                feature.getPlugin().getLogger().info("\"tanks.yml\" was not found!");
                feature.getPlugin().getLogger().info("Creating a new one!");
                tanksDatabase.createNewFile();
                tanks = YamlConfiguration.loadConfiguration(tanksDatabase);
            } else {
                feature.getPlugin().getLogger().info("Tank database found, loading all the tanks!");
                tanks = YamlConfiguration.loadConfiguration(tanksDatabase);
                this.loadTanks();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void loadTanks() {
        ArrayList<String> arrayList = new ArrayList<>();
        for (World object : feature.getPlugin().getServer().getWorlds()) {
            arrayList.add(object.getName());
        }
        int n = 0;
        try {
            for (String string : tanks.getConfigurationSection("tanks").getKeys(false)) {
                ++n;
                int n2 = 0;
                String string2 = null;
                String[] arrstring = string.split("_");
                int n3 = Integer.parseInt(arrstring[0]);
                int n4 = Integer.parseInt(arrstring[1]);
                int n5 = Integer.parseInt(arrstring[2]);
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 3; i <= arrstring.length - 1; ++i) {
                    stringBuilder.append(arrstring[i]);
                    if (i == arrstring.length - 1)
                        continue;
                    stringBuilder.append("_");
                }
                if (arrayList.contains(stringBuilder.toString())) {
                    World world = feature.getPlugin().getServer().getWorld(stringBuilder.toString());
                    for (String string3 : tanks.getConfigurationSection("tanks." + string).getKeys(false)) {
                        if (string3.contains("tankType")) {
                            string2 = tanks.getString("tanks." + string + "." + string3);
                            continue;
                        }
                        if (!string3.contains("quantity"))
                            continue;
                        n2 = tanks.getInt("tanks." + string + "." + string3);
                    }
                    Location location = new Location(world, n3, n4, n5);
                    this.createLiquidTank(location, TankType.getTankType(string2), n2);
                    continue;
                }
                this.unloadedTankList.add(new UnloadedTank(stringBuilder.toString(), n3, n4, n5, TankType.getTankType(string2), n2));
            }
        } catch (Exception exception) {
            // empty catch block
        }
        feature.getPlugin().getLogger().info("Loaded " + n + " Liquid tanks!");
    }

    private void loadUnloadedTankList(World world) {
        for (UnloadedTank unloadedTank : this.unloadedTankList) {
            if (!unloadedTank.getWorld().equalsIgnoreCase(world.getName()))
                continue;
            this.createLiquidTank(new Location(world, unloadedTank.getX(), unloadedTank.getY(), unloadedTank.getZ()), unloadedTank.getType(),
                    unloadedTank.getQuantity());
            this.unloadedTankList.remove(unloadedTank);
        }
    }

    public void save() {
        feature.getPlugin().getLogger().info("Saving Liquid Tanks!");
        int n = this.quickSave(false, true);
        feature.getPlugin().getLogger().info("Saved " + n + " Liquid Tanks!");
    }

    public int quickSave(boolean bl, boolean bl2) {
        Location object;
        if (bl) {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() ->  this.quickSave(false, bl2));
            return 0;
        }
        int n = 0;
        tanks.set("tanks", "");
        try {
            tanks.save(tanksDatabase);
        } catch (IOException iOException) {
            // empty catch block
        }
        for (AbstractTank object2 : this.tankList) {
            ++n;
            object = object2.getLocation().clone();
            String string = object.getBlockX() + "_" + object.getBlockY() + "_" + object.getBlockZ() + "_" + object.getWorld().getName();
            tanks.set("tanks." + string + ".tankType", object2.getTankType().toString().toLowerCase().replace("_", ""));
            tanks.set("tanks." + string + ".quantity", object2.getQuantity());
            if (!bl2)
                continue;
            object2.clear(true);
        }
        for (UnloadedTank unloadedTank : this.unloadedTankList) {
            ++n;
            String string = unloadedTank.getX() + "_" + unloadedTank.getY() + "_" + unloadedTank.getZ() + "_" + unloadedTank.getWorld();
            tanks.set("tanks." + string + ".tankType", unloadedTank.getType().toString().toLowerCase().replace("_", ""));
            tanks.set("tanks." + string + ".quantity", unloadedTank.getQuantity());
        }
        try {
            tanks.save(tanksDatabase);
        } catch (IOException iOException) {
            // empty catch block
        }
        return n;
    }

    public AbstractTank getTank(Location location) {
        for (AbstractTank liquidTank : this.tankList) {
            if (!liquidTank.getLocation().equals(location))
                continue;
            return liquidTank;
        }
        return null;
    }

    public boolean canPlaceTank(Location location) {
        String string = location.getBlockX() / 16 + "," + location.getBlockZ() / 16;
        int n = 0;
        for (AbstractTank liquidTank : this.tankList) {
            Location location2 = liquidTank.getLocation().clone();
            if (!string.equalsIgnoreCase(location2.getBlockX() / 16 + "," + location2.getBlockZ() / 16))
                continue;
            ++n;
        }
        return n < this.maxAmountPerChunk;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void placeOfLiquidTank(BlockPlaceEvent blockPlaceEvent) {
        if (blockPlaceEvent.isCancelled()) {
            return;
        }
        try {
            if (blockPlaceEvent.getBlock().getType() != Material.HOPPER || !blockPlaceEvent.getItemInHand().getItemMeta().getDisplayName().equals(this.itemName)) {
                return;
            }
            if (blockPlaceEvent.getPlayer().hasPermission("liquidtanks.use") || !(boolean)feature.getConfigHandler().getSetting("enable-permission")) {
                if (blockPlaceEvent.getPlayer().hasPermission("liquidtanks.limit.bypass") || this.canPlaceTank(blockPlaceEvent.getBlock().getLocation())) {
                    this.createLiquidTank(blockPlaceEvent.getBlock().getLocation());

                    if (this.enableItems) {
                        this.addItems(blockPlaceEvent.getBlock());
                    }
                } else {
                    MessageUtils.sendTitle(blockPlaceEvent.getPlayer(),
                            "&cYou can only place down " + this.maxAmountPerChunk + " per chunk!");
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
            if (object.getType() != Material.HOPPER || (liquidTank = this.getTank(object.getLocation())) == null)
                continue;
            arrayList.add(liquidTank);
        }
        for (AbstractTank liquidTank : arrayList) {
            this.removeTank(liquidTank);
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
        AbstractTank liquidTank = this.getTank(blockBreakEvent.getBlock().getLocation());
        if (liquidTank != null) {
            blockBreakEvent.setCancelled(true);
            if (!blockBreakEvent.getPlayer().getGameMode().equals(GameMode.CREATIVE) && liquidTank instanceof ExperienceTank) {
                int n = liquidTank.getQuantity();
                ExperienceOrb experienceOrb = liquidTank.getLocation().getWorld().spawn(liquidTank.getLocation().clone().add(0.5, 0.5, 0.5), ExperienceOrb.class);
                experienceOrb.setExperience(n);
            }
            this.removeTank(liquidTank);
            Hopper hopper = (Hopper) blockBreakEvent.getBlock().getState();
            hopper.getInventory().clear();
            if (!blockBreakEvent.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                blockBreakEvent.getBlock().getWorld().dropItemNaturally(blockBreakEvent.getBlock().getLocation().clone().add(0.5, 0.5, 0.5),
                        ItemCreator.newItem(Material.HOPPER, 1, this.itemName, ""));
            }
            blockBreakEvent.getBlock().setType(Material.AIR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void rightClickOnLiquidTank(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.isCancelled()) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        if (!(player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode().equals(GameMode.ADVENTURE) || player.getGameMode().equals(GameMode.CREATIVE) || playerInteractEvent.getAction().equals(Action.RIGHT_CLICK_BLOCK) && playerInteractEvent.getClickedBlock().getType() == Material.HOPPER)) {
            return;
        }
        if (player.isSneaking()) {
            if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                playerInteractEvent.setCancelled(true);
            }
        }
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() ->  {
            try {
                AbstractTank liquidTank = this.getTank(Objects.requireNonNull(playerInteractEvent.getClickedBlock()).getLocation());
                if (liquidTank != null) {
                    playerInteractEvent.setCancelled(true);
                    if (!player.hasPermission("liquidtanks.use") && (boolean) feature.getConfigHandler().getSetting("enable-permission")) {
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
            this.loadUnloadedTankList(playerTeleportEvent.getTo().getWorld());
        } catch (Exception exception) {
            // empty catch block
        }
        for (AbstractTank liquidTank : this.tankList) {
            liquidTank.updatePlayerView();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            this.loadUnloadedTankList(playerJoinEvent.getPlayer().getWorld());
            for (AbstractTank liquidTank : this.tankList) {
                liquidTank.updatePlayerView(playerJoinEvent.getPlayer());
            }
        }, 0L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent worldLoadEvent) {
        this.loadUnloadedTankList(worldLoadEvent.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent worldUnloadEvent) {
        ArrayList<AbstractTank> arrayList = new ArrayList<>();
        for (AbstractTank liquidTank : this.tankList) {
            if (liquidTank.getLocation().getWorld() != worldUnloadEvent.getWorld())
                continue;
            arrayList.add(liquidTank);
            this.unloadedTankList.add(new UnloadedTank(worldUnloadEvent.getWorld().getName(), liquidTank.getLocation().getBlockX(), liquidTank.getLocation().getBlockY(),
                    liquidTank.getLocation().getBlockZ(), liquidTank.getTankType(), liquidTank.getQuantity()));
        }
        for (AbstractTank liquidTank : arrayList) {
            feature.getTankManager().removeTank(liquidTank);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        for (AbstractTank liquidTank : this.tankList) {
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
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryMoveItemEvent(InventoryMoveItemEvent inventoryMoveItemEvent) {
        AbstractTank liquidTank;
        Hopper hopper;
        InventoryHolder inventoryHolder;
        if (inventoryMoveItemEvent.getSource().getType().equals(InventoryType.HOPPER) && (inventoryHolder = inventoryMoveItemEvent.getSource().getHolder()) != null
                && inventoryHolder instanceof Hopper) {
            hopper = (Hopper) inventoryHolder;
            liquidTank = this.getTank(hopper.getLocation());
            if (!hopper.getBlock().isBlockIndirectlyPowered() && !hopper.getBlock().isBlockPowered() && liquidTank != null) {
                InventoryHolder inventoryHolder2;
                if (!this.enableItems) {
                    hopper.getInventory().clear();
                }
                inventoryMoveItemEvent.setCancelled(true);
                if (inventoryMoveItemEvent.getDestination().getType().equals(InventoryType.HOPPER)
                        && (inventoryHolder2 = inventoryMoveItemEvent.getDestination().getHolder()) != null && inventoryHolder2 instanceof Hopper) {
                    Hopper hopper2 = (Hopper) inventoryHolder2;
                    AbstractTank liquidTank2 = this.getTank(hopper2.getLocation());
                    if (!hopper2.getBlock().isBlockIndirectlyPowered() && !hopper2.getBlock().isBlockPowered() && liquidTank2 != null) {
                        if (!this.enableItems) {
                            hopper2.getInventory().clear();
                        }
                        if (!(liquidTank2.isOnCooldown() || liquidTank.isOnCooldown() || liquidTank.getTankType().equals(TankType.EMPTY))) {
                            if (liquidTank.getTankType().equals(liquidTank2.getTankType())) {
                                if (liquidTank2.getQuantity() < liquidTank2.getMaxQuantity()) {
                                    if (liquidTank.getQuantity() == liquidTank.getMaxQuantity() - liquidTank2.getQuantity()) {
                                        AbstractTank liquidTank3 = this.emptyTank(liquidTank);
                                        liquidTank3.setOnCooldown();
                                        liquidTank2.setQuantity(liquidTank2.getMaxQuantity());
                                        liquidTank2.setOnCooldown();
                                    } else if (liquidTank.getQuantity() < liquidTank.getMaxQuantity() - liquidTank2.getQuantity()) {
                                        liquidTank2.setQuantity(liquidTank2.getQuantity() + liquidTank.getQuantity());
                                        AbstractTank liquidTank4 = this.emptyTank(liquidTank);
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
                                    AbstractTank liquidTank5 = this.changeTankType(liquidTank2, liquidTank.getTankType(), liquidTank.getMaxQuantity());
                                    liquidTank5.setOnCooldown();
                                    liquidTank.setQuantity(liquidTank.getQuantity() - liquidTank.getMaxQuantity());
                                    liquidTank.setOnCooldown();
                                } else {
                                    AbstractTank liquidTank6 = this.changeTankType(liquidTank2, liquidTank.getTankType(), liquidTank.getQuantity());
                                    liquidTank6.setOnCooldown();
                                    AbstractTank liquidTank7 = this.emptyTank(liquidTank);
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
                && (liquidTank = this.getTank((hopper = (Hopper) inventoryHolder).getLocation())) != null) {
            inventoryMoveItemEvent.setCancelled(true);
            if (!this.enableItems) {
                hopper.getInventory().clear();
            }
        }
    }
}
