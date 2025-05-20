package nl.hauntedmc.serverfeatures.features.liquidtank.config;

import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LiquidTankDataHandler {

    private final LiquidTank feature;
    private final ResourceHandler resourceHandler;
    private final FileConfiguration config;

    // Lists to keep track of loaded tanks and tanks from unloaded worlds.
    private final List<AbstractTank> tankList = new ArrayList<>();
    private final List<UnloadedTank> unloadedTankList = new ArrayList<>();

    public LiquidTankDataHandler(LiquidTank feature) {
        this.feature = feature;
        this.resourceHandler = new ResourceHandler(feature.getPlugin(), "liquidtanks.yml");
        this.config = resourceHandler.getConfig();
    }

    /**
     * Parses the configuration section "tanks" and creates tanks.
     * For worlds that are loaded, delegates creation to the TankManager.
     * Otherwise, adds an UnloadedTank entry.
     */
    public void loadTanks() {
        ArrayList<String> loadedWorlds = new ArrayList<>();
        for (World world : feature.getPlugin().getServer().getWorlds()) {
            loadedWorlds.add(world.getName());
        }
        int count = 0;
        try {
            if (config.getConfigurationSection("tanks") == null) {
                return;
            }
            for (String key : Objects.requireNonNull(config.getConfigurationSection("tanks")).getKeys(false)) {
                count++;
                int quantity = 0;
                String tankTypeStr = null;
                // Expecting key format: x_y_z_worldName
                String[] parts = key.split("_");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                StringBuilder worldBuilder = new StringBuilder();
                for (int i = 3; i < parts.length; i++) {
                    worldBuilder.append(parts[i]);
                    if (i != parts.length - 1) {
                        worldBuilder.append("_");
                    }
                }
                String worldName = worldBuilder.toString();
                if (loadedWorlds.contains(worldName)) {
                    World world = feature.getPlugin().getServer().getWorld(worldName);
                    if (config.getConfigurationSection("tanks." + key) != null) {
                        for (String subKey : config.getConfigurationSection("tanks." + key).getKeys(false)) {
                            if (subKey.contains("tankType")) {
                                tankTypeStr = config.getString("tanks." + key + "." + subKey);
                            }
                            if (subKey.contains("quantity")) {
                                quantity = config.getInt("tanks." + key + "." + subKey);
                            }
                        }
                        Location location = new Location(world, x, y, z);
                        feature.getTankManager().createLiquidTank(location, TankType.getTankType(tankTypeStr), quantity);
                    }
                } else {
                    unloadedTankList.add(new UnloadedTank(worldName, x, y, z, TankType.getTankType(tankTypeStr), quantity));
                }
            }
        } catch (Exception e) {
            // Optionally log error.
        }
        feature.getLogger().info("Loaded " + count + " Liquid tanks!");
    }

    /**
     * Saves the current tank data to storage.
     */
    public void save() {
        feature.getLogger().info("Saving Liquid Tanks!");
        int savedCount = quickSave(false, true);
        feature.getLogger().info("Saved " + savedCount + " Liquid Tanks!");
    }

    /**
     * Saves the current tankList and unloadedTankList to the configuration.
     *
     * @param async      whether to run the save asynchronously
     * @param clearAfter if true, calls clear(true) on each loaded tank after saving
     * @return the number of tank entries saved
     */
    public int quickSave(boolean async, boolean clearAfter) {
        if (async) {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> quickSave(false, clearAfter));
            return 0;
        }
        int count = 0;
        // Clear the "tanks" section.
        config.set("tanks", null);
        try {
            resourceHandler.save();
        } catch (Exception e) {
            // Handle exception if necessary.
        }
        // Save each loaded tank.
        for (AbstractTank tank : tankList) {
            count++;
            Location loc = tank.getLocation().clone();
            String key = loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + loc.getWorld().getName();
            config.set("tanks." + key + ".tankType", tank.getTankType().toString().toLowerCase().replace("_", ""));
            config.set("tanks." + key + ".quantity", tank.getQuantity());
            if (clearAfter) {
                tank.clear(true);
            }
        }
        // Save unloaded tanks.
        for (UnloadedTank unloaded : unloadedTankList) {
            count++;
            String key = unloaded.getX() + "_" + unloaded.getY() + "_" + unloaded.getZ() + "_" + unloaded.getWorld();
            config.set("tanks." + key + ".tankType", unloaded.getType().toString().toLowerCase().replace("_", ""));
            config.set("tanks." + key + ".quantity", unloaded.getQuantity());
        }
        resourceHandler.save();
        return count;
    }

    public List<AbstractTank> getTankList() {
        return tankList;
    }

    public List<UnloadedTank> getUnloadedTankList() {
        return unloadedTankList;
    }

    public void addTank(AbstractTank tank) {
        tankList.add(tank);
    }

    public void removeTank(AbstractTank tank) {
        tankList.remove(tank);
    }
}
