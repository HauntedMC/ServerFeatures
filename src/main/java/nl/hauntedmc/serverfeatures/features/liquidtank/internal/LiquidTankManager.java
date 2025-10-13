package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.config.LiquidTankDataHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.*;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public class LiquidTankManager implements Listener {

    private final LiquidTank feature;
    // Configuration options.
    private int maxAmountPerChunk;
    private String itemName;
    private boolean enableItems;

    // Our new data handler for persistence.
    private LiquidTankDataHandler dataHandler;
    private static final long delay = 100L;

    public LiquidTankManager(LiquidTank feature) {
        this.feature = feature;
    }


    public static void gameLoop(LiquidTank feature) {
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                gameTick(feature);
            } catch (Exception ignored) {
            }
        }, BukkitTime.ticks(delay), BukkitTime.ticks(delay));
    }

    private static void gameTick(LiquidTank feature) {
        ArrayList<AbstractTank> arrayList = new ArrayList<>();
        for (AbstractTank abstractTank : feature.getTankManager().getTankList()) {
            if (BlockUtils.isLoaded(abstractTank.getLocation()) && abstractTank.getLocation().getBlock().getType() != Material.HOPPER)
                arrayList.add(abstractTank);
        }
        for (AbstractTank abstractTank : arrayList)
            feature.getTankManager().removeTank(abstractTank);
    }

    public void initialize() {
        // Initialize the data handler.
        this.dataHandler = new LiquidTankDataHandler(feature);
        this.dataHandler.loadTanks();

        gameLoop(feature);
        ExperienceTank.gameLoop(feature);

        readConfigOptions();
    }

    private void readConfigOptions() {
        maxAmountPerChunk = (int) feature.getConfigHandler().getSetting("amount-per-chunk");
        itemName = ((String) feature.getConfigHandler().getSetting("item-name"));
        enableItems = (boolean) feature.getConfigHandler().getSetting("enable-items");
    }

    public List<AbstractTank> getTankList() {
        return dataHandler.getTankList();
    }

    public void createLiquidTank(Location location) {
        EmptyTank emptyTank = new EmptyTank(location, feature);
        dataHandler.addTank(emptyTank);
        dataHandler.quickSave(true, false);
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
        dataHandler.addTank(liquidTank);
    }

    public AbstractTank changeTankType(AbstractTank liquidTank, TankType tankType, int n) {
        liquidTank.clear();
        AbstractTank newTank = tankType.equals(TankType.LAVA) ? new LavaTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.WATER) ? new WaterTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.MILK) ? new MilkTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.MUSHROOM_STEW) ? new MushroomStewTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.RABBIT_STEW) ? new RabbitStewTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.BEETROOT_SOUP) ? new BeetrootSoupTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.DRAGON_BREATH) ? new DragonBreathTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.EXPERIENCE) ? new ExperienceTank(liquidTank.getLocation(), n, feature)
                : (tankType.equals(TankType.HONEY) ? new HoneyTank(liquidTank.getLocation(), n, feature)
                : new EmptyTank(liquidTank.getLocation(), feature)))))))));
        dataHandler.removeTank(liquidTank);
        dataHandler.addTank(newTank);
        return newTank;
    }

    public AbstractTank emptyTank(AbstractTank liquidTank) {
        liquidTank.clear();
        dataHandler.removeTank(liquidTank);
        EmptyTank emptyTank = new EmptyTank(liquidTank.getLocation(), feature);
        dataHandler.addTank(emptyTank);
        return emptyTank;
    }

    public void removeTank(AbstractTank liquidTank) {
        liquidTank.clear();
        dataHandler.removeTank(liquidTank);
    }

    public void loadUnloadedTankList(World world) {
        for (UnloadedTank unloaded : dataHandler.getUnloadedTankList()) {
            if (!unloaded.getWorld().equalsIgnoreCase(world.getName()))
                continue;
            createLiquidTank(new Location(world, unloaded.getX(), unloaded.getY(), unloaded.getZ()),
                    unloaded.getType(), unloaded.getQuantity());
            dataHandler.getUnloadedTankList().remove(unloaded);
        }
    }

    public void save() {
        dataHandler.save();
    }

    public AbstractTank getTank(Location location) {
        for (AbstractTank tank : dataHandler.getTankList()) {
            if (tank.getLocation().equals(location)) {
                return tank;
            }
        }
        return null;
    }

    public boolean canPlaceTank(Location location) {
        String chunkKey = (location.getBlockX() / 16) + "," + (location.getBlockZ() / 16);
        int count = 0;
        for (AbstractTank tank : dataHandler.getTankList()) {
            Location loc = tank.getLocation().clone();
            String key = (loc.getBlockX() / 16) + "," + (loc.getBlockZ() / 16);
            if (chunkKey.equalsIgnoreCase(key))
                count++;
        }
        return count < maxAmountPerChunk;
    }

    public boolean isEnableItems() {
        return enableItems;
    }

    public int getMaxAmountPerChunk() {
        return maxAmountPerChunk;
    }

    public String getItemName() {
        return itemName;
    }

    public List<UnloadedTank> getUnloadedTankList() {
        return dataHandler.getUnloadedTankList();
    }
}
