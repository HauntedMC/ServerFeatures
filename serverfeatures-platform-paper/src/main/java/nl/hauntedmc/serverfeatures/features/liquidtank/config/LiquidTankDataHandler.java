package nl.hauntedmc.serverfeatures.features.liquidtank.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class LiquidTankDataHandler extends ConfigView {

    private final LiquidTank feature;
    private final List<UnloadedTank> unloadedTankList = new ArrayList<>();
    private final AtomicLong requestedWrite = new AtomicLong();
    private final Object writeLock = new Object();

    public LiquidTankDataHandler(LiquidTank feature) {
        super(feature.getPlugin().getConfigService().open("local/liquidtanks.yml", true), "");
        this.feature = feature;
    }

    public void loadTanks() {
        unloadedTankList.clear();
        int count = 0;
        Map<String, ConfigNode> children = node("tanks").children();
        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            ParsedKey parsed = parseKey(entry.getKey());
            if (parsed == null) {
                feature.getLogger().warning(
                        "[LiquidTanks] Invalid tank key '" + entry.getKey()
                                + "' — expected format x_y_z_worldName"
                );
                continue;
            }

            ConfigNode tankNode = entry.getValue();
            TankType tankType = TankType.getTankType(
                    tankNode.get("tankType").as(String.class, "empty")
            );
            int quantity = tankNode.get("quantity").as(Integer.class, 0);
            World world = feature.getPlugin().getServer().getWorld(parsed.worldName());
            if (world == null) {
                unloadedTankList.add(new UnloadedTank(
                        parsed.worldName(),
                        parsed.x(),
                        parsed.y(),
                        parsed.z(),
                        tankType,
                        quantity
                ));
            } else {
                feature.getTankManager().restoreLiquidTank(
                        new Location(world, parsed.x(), parsed.y(), parsed.z()),
                        tankType,
                        quantity
                );
            }
            count++;
        }
        feature.getLogger().info("Loaded " + count + " Liquid tanks!");
    }

    /** Captures Bukkit state on the main thread and writes only immutable data asynchronously. */
    public void saveAsync() {
        SaveSnapshot snapshot = captureSnapshot();
        long version = requestedWrite.incrementAndGet();
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(
                () -> writeIfLatest(version, snapshot)
        );
    }

    /** Writes the final snapshot after all older asynchronous writes have completed or become stale. */
    public void save() {
        feature.getLogger().info("Saving Liquid Tanks!");
        SaveSnapshot snapshot = captureSnapshot();
        requestedWrite.incrementAndGet();
        synchronized (writeLock) {
            writeSnapshot(snapshot);
        }
        feature.getLogger().info("Saved " + snapshot.tanks().size() + " Liquid Tanks!");
    }

    public List<UnloadedTank> getUnloadedTankList() {
        return unloadedTankList;
    }

    private SaveSnapshot captureSnapshot() {
        List<TankSnapshot> tanks = new ArrayList<>();
        for (AbstractTank tank : feature.getTankManager().getTankList()) {
            Location location = tank.getLocation();
            tanks.add(new TankSnapshot(
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    tank.getTankType(),
                    tank.getQuantity()
            ));
        }
        for (UnloadedTank tank : unloadedTankList) {
            tanks.add(new TankSnapshot(
                    tank.getWorld(),
                    tank.getX(),
                    tank.getY(),
                    tank.getZ(),
                    tank.getType(),
                    tank.getQuantity()
            ));
        }
        return new SaveSnapshot(List.copyOf(tanks));
    }

    private void writeIfLatest(long version, SaveSnapshot snapshot) {
        synchronized (writeLock) {
            if (version != requestedWrite.get()) {
                return;
            }
            writeSnapshot(snapshot);
        }
    }

    private void writeSnapshot(SaveSnapshot snapshot) {
        LinkedHashMap<String, Object> tanksOut = new LinkedHashMap<>();
        for (TankSnapshot tank : snapshot.tanks()) {
            String key = tank.x() + "_" + tank.y() + "_" + tank.z() + "_" + tank.worldName();
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("tankType", tank.type().name().toLowerCase(java.util.Locale.ROOT).replace("_", ""));
            node.put("quantity", tank.quantity());
            tanksOut.put(key, node);
        }
        put("tanks", tanksOut);
    }

    static ParsedKey parseKey(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split("_");
        if (parts.length < 4) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String worldName = String.join("_", java.util.Arrays.copyOfRange(parts, 3, parts.length));
            return worldName.isEmpty() ? null : new ParsedKey(x, y, z, worldName);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record ParsedKey(int x, int y, int z, String worldName) {
    }

    private record TankSnapshot(
            String worldName,
            int x,
            int y,
            int z,
            TankType type,
            int quantity
    ) {
    }

    private record SaveSnapshot(List<TankSnapshot> tanks) {
    }
}
