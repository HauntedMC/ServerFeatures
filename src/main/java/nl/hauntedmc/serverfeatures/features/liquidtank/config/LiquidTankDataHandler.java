package nl.hauntedmc.serverfeatures.features.liquidtank.config;

import nl.hauntedmc.serverfeatures.api.io.resources.ResourceHandler;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LiquidTankDataHandler {

    private final LiquidTank feature;
    private final ResourceHandler resourceHandler;
    private final FileConfiguration config;

    // Lists to keep track of loaded tanks and tanks from unloaded worlds.
    private final List<AbstractTank> tankList = new ArrayList<>();
    private final List<UnloadedTank> unloadedTankList = new ArrayList<>();

    public LiquidTankDataHandler(LiquidTank feature) {
        this.feature = feature;
        this.resourceHandler = new ResourceHandler(feature.getPlugin(), "local/liquidtanks.yml");
        this.config = resourceHandler.getConfig();
    }

    /**
     * Parses the configuration section "tanks" and creates tanks.
     * For worlds that are loaded, delegates creation to the TankManager.
     * Otherwise, adds an UnloadedTank entry.
     */
    public void loadTanks() {
        tankList.clear();
        unloadedTankList.clear();

        int count = 0;

        ConfigNode tanksNode = ConfigNode.ofRaw(config.get("tanks"), "local/liquidtanks.yml:tanks");
        Map<String, ConfigNode> children = tanksNode.children();
        if (children.isEmpty()) {
            feature.getLogger().info("Loaded 0 Liquid tanks!");
            return;
        }

        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            String key = entry.getKey(); // expected: x_y_z_worldName (world may contain underscores)
            ParsedKey parsed = parseKey(key);
            if (parsed == null) {
                feature.getLogger().warning("[LiquidTanks] Invalid tank key '" + key + "' — expected format x_y_z_worldName");
                continue;
            }

            ConfigNode n = entry.getValue();
            String tankTypeStr = n.get("tankType").as(String.class, null);
            int quantity = n.get("quantity").as(Integer.class, 0);

            World world = feature.getPlugin().getServer().getWorld(parsed.worldName);
            if (world != null) {
                Location location = new Location(world, parsed.x, parsed.y, parsed.z);
                feature.getTankManager().createLiquidTank(location, TankType.getTankType(tankTypeStr), quantity);
            } else {
                unloadedTankList.add(new UnloadedTank(parsed.worldName, parsed.x, parsed.y, parsed.z,
                        TankType.getTankType(tankTypeStr), quantity));
            }
            count++;
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

        // Build a plain nested map (tanks -> key -> { tankType, quantity }) and set once.
        java.util.LinkedHashMap<String, Object> tanksOut = new java.util.LinkedHashMap<>();

        // Save each loaded tank.
        for (AbstractTank tank : tankList) {
            Location loc = tank.getLocation().clone();
            String key = loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + loc.getWorld().getName();

            java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
            node.put("tankType", tank.getTankType().toString().toLowerCase().replace("_", ""));
            node.put("quantity", tank.getQuantity());
            tanksOut.put(key, node);
            count++;

            if (clearAfter) {
                tank.clear();
            }
        }

        // Save unloaded tanks.
        for (UnloadedTank unloaded : unloadedTankList) {
            String key = unloaded.getX() + "_" + unloaded.getY() + "_" + unloaded.getZ() + "_" + unloaded.getWorld();

            java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
            node.put("tankType", unloaded.getType().toString().toLowerCase().replace("_", ""));
            node.put("quantity", unloaded.getQuantity());
            tanksOut.put(key, node);
            count++;
        }

        // Overwrite section once and save file.
        config.set("tanks", tanksOut);
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

    // -----------------
    // helpers
    // -----------------

    private static final class ParsedKey {
        final int x, y, z;
        final String worldName;
        ParsedKey(int x, int y, int z, String worldName) {
            this.x = x; this.y = y; this.z = z; this.worldName = worldName;
        }
    }

    /**
     * Parses a key in the form x_y_z_worldName (worldName may contain underscores).
     */
    private ParsedKey parseKey(String key) {
        if (key == null) return null;
        String[] parts = key.split("_");
        if (parts.length < 4) return null;

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            // Re-join the rest for world name (supports underscores)
            StringBuilder worldBuilder = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) worldBuilder.append('_');
                worldBuilder.append(parts[i]);
            }
            String worldName = worldBuilder.toString();
            if (worldName.isEmpty()) return null;

            return new ParsedKey(x, y, z, worldName);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
