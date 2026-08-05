package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.config.LiquidTankDataHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankChunkKey;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankPosition;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.UnloadedTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.BeetrootSoupTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.DragonBreathTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.EmptyTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.ExperienceTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.HoneyTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.LavaTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.MilkTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.MushroomStewTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.RabbitStewTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.WaterTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class LiquidTankManager {

    private static final long VALIDATION_INTERVAL_TICKS = 1_200L;
    private static final long SAVE_COALESCE_TICKS = 100L;
    private static final int VIEW_DISTANCE_BLOCKS = 20;
    private static final int VIEW_CHUNK_RADIUS = (VIEW_DISTANCE_BLOCKS + 15) >> 4;

    private final LiquidTank feature;
    private final TankIndex tankIndex = new TankIndex();
    private final TankVisibilityTracker visibility = new TankVisibilityTracker();

    private int maxAmountPerChunk;
    private String itemName;
    private boolean enableItems;
    private boolean permissionRequired;
    private LiquidTankDataHandler dataHandler;
    private BukkitTask pendingSave;
    private int experienceTankCount;
    private boolean shuttingDown;

    public LiquidTankManager(LiquidTank feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    public void initialize() {
        shuttingDown = false;
        readConfigOptions();
        dataHandler = new LiquidTankDataHandler(feature);
        dataHandler.loadTanks();
        startValidationLoop();
        ExperienceTank.startGameLoop(feature);
        refreshAllPlayerViews();
    }

    public void shutdown() {
        shuttingDown = true;
        if (pendingSave != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pendingSave);
            pendingSave = null;
        }
        if (dataHandler != null) {
            dataHandler.save();
        }
        for (AbstractTank tank : tankIndex.snapshot()) {
            tank.clear();
        }
        visibility.clear();
        tankIndex.clear();
        experienceTankCount = 0;
    }

    private void readConfigOptions() {
        maxAmountPerChunk = feature.getConfigHandler()
                .node("amount-per-chunk")
                .as(Integer.class, 16);
        itemName = feature.getConfigHandler()
                .node("item-name")
                .as(String.class, "&bLiquid Tank");
        enableItems = feature.getConfigHandler()
                .node("enable-items")
                .as(Boolean.class, true);
        permissionRequired = feature.getConfigHandler()
                .node("enable-permission")
                .as(Boolean.class, false);
    }

    private void startValidationLoop() {
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::validateTankBlocks,
                BukkitTime.ticks(VALIDATION_INTERVAL_TICKS),
                BukkitTime.ticks(VALIDATION_INTERVAL_TICKS)
        );
    }

    private void validateTankBlocks() {
        for (AbstractTank tank : tankIndex.snapshot()) {
            try {
                Location location = tank.getLocation();
                if (BlockUtils.isLoaded(location)
                        && location.getBlock().getType() != Material.HOPPER) {
                    removeTank(tank);
                }
            } catch (RuntimeException exception) {
                feature.getLogger().log(
                        Level.WARNING,
                        "Could not validate liquid tank at " + tank.getLocation(),
                        exception
                );
            }
        }
    }

    public void createLiquidTank(Location location) {
        registerTank(new EmptyTank(blockLocation(location), feature), true);
        markDirty();
    }

    /** Restores a persisted tank without scheduling another persistence write. */
    public void restoreLiquidTank(Location location, TankType tankType, int quantity) {
        registerTank(createTank(blockLocation(location), tankType, quantity), false);
    }

    /** Kept for callers that restore tanks after a world becomes available. */
    public void createLiquidTank(Location location, TankType tankType, int quantity) {
        restoreLiquidTank(location, tankType, quantity);
    }

    public AbstractTank changeTankType(AbstractTank tank, TankType tankType, int quantity) {
        Objects.requireNonNull(tank, "tank");
        Location location = blockLocation(tank.getLocation());
        if (tankIndex.get(TankPosition.of(location)) != tank) {
            throw new IllegalStateException("Cannot replace a liquid tank that is no longer registered");
        }
        AbstractTank replacement = createTank(location, tankType, quantity);
        if (!unregisterTank(tank)) {
            replacement.clear();
            throw new IllegalStateException("Cannot replace a liquid tank that is no longer registered");
        }
        registerTank(replacement, true);
        markDirty();
        return replacement;
    }

    public AbstractTank emptyTank(AbstractTank tank) {
        return changeTankType(tank, TankType.EMPTY, 0);
    }

    public void removeTank(AbstractTank tank) {
        if (unregisterTank(tank)) {
            markDirty();
        }
    }

    public AbstractTank getTank(Location location) {
        return location == null ? null : tankIndex.get(TankPosition.of(location));
    }

    public AbstractTank getTank(Block block) {
        return block == null ? null : tankIndex.get(TankPosition.of(block));
    }

    public boolean canPlaceTank(Location location) {
        return tankIndex.count(TankChunkKey.of(location)) < maxAmountPerChunk;
    }

    public List<AbstractTank> getTankList() {
        return tankIndex.snapshot();
    }

    public void refreshPlayerView(Player player, Location playerLocation) {
        if (playerLocation == null || playerLocation.getWorld() == null) {
            forgetPlayer(player);
            return;
        }
        TankChunkKey center = TankChunkKey.of(playerLocation);
        Set<AbstractTank> desired = new HashSet<>();
        for (AbstractTank tank : tankIndex.nearby(
                center.worldId(),
                center.x(),
                center.z(),
                VIEW_CHUNK_RADIUS
        )) {
            if (tank.isVisibleFrom(playerLocation) && isTankChunkSent(player, tank)) {
                desired.add(tank);
            }
        }

        TankVisibilityTracker.Delta delta = visibility.update(player.getUniqueId(), desired);
        for (AbstractTank removed : delta.removed()) {
            removed.hideFrom(player);
        }
        for (AbstractTank added : delta.added()) {
            added.showTo(player);
        }
    }

    public void refreshPlayerView(Player player) {
        refreshPlayerView(player, player.getLocation());
    }

    public void forgetPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        for (AbstractTank tank : visibility.removePlayer(playerId)) {
            tank.forgetViewer(playerId);
        }
    }

    public boolean hasTankInChunk(Chunk chunk) {
        return tankIndex.hasTanks(new TankChunkKey(
                chunk.getWorld().getUID(),
                chunk.getX(),
                chunk.getZ()
        ));
    }

    public void refreshPlayerChunk(Player player, Chunk chunk) {
        Set<AbstractTank> tanks = tankIndex.tanks(new TankChunkKey(
                chunk.getWorld().getUID(),
                chunk.getX(),
                chunk.getZ()
        ));
        if (tanks.isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        visibility.forget(playerId, tanks);
        for (AbstractTank tank : tanks) {
            tank.forgetViewer(playerId);
        }
        refreshPlayerView(player);
    }

    public void loadUnloadedTankList(World world) {
        boolean changed = false;
        Iterator<UnloadedTank> iterator = dataHandler.getUnloadedTankList().iterator();
        while (iterator.hasNext()) {
            UnloadedTank unloaded = iterator.next();
            if (!unloaded.getWorld().equalsIgnoreCase(world.getName())) {
                continue;
            }
            restoreLiquidTank(
                    new Location(world, unloaded.getX(), unloaded.getY(), unloaded.getZ()),
                    unloaded.getType(),
                    unloaded.getQuantity()
            );
            iterator.remove();
            changed = true;
        }
        if (changed) {
            refreshAllPlayerViews();
        }
    }

    public void unloadWorld(World world) {
        for (AbstractTank tank : tankIndex.snapshot()) {
            if (tank.getLocation().getWorld() != world) {
                continue;
            }
            Location location = tank.getLocation();
            dataHandler.getUnloadedTankList().add(new UnloadedTank(
                    world.getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    tank.getTankType(),
                    tank.getQuantity()
            ));
            unregisterTank(tank);
        }
        markDirty();
    }

    public void markDirty() {
        if (shuttingDown || dataHandler == null) {
            return;
        }
        if (pendingSave != null) {
            return;
        }
        pendingSave = feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            pendingSave = null;
            dataHandler.saveAsync();
        }, BukkitTime.ticks(SAVE_COALESCE_TICKS));
    }

    public boolean isEnableItems() {
        return enableItems;
    }

    public boolean isPermissionRequired() {
        return permissionRequired;
    }

    public boolean hasExperienceTanks() {
        return experienceTankCount > 0;
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

    private AbstractTank createTank(Location location, TankType type, int quantity) {
        TankType effectiveType = type == null || quantity <= 0 ? TankType.EMPTY : type;
        return switch (effectiveType) {
            case LAVA -> new LavaTank(location, quantity, feature);
            case WATER -> new WaterTank(location, quantity, feature);
            case MILK -> new MilkTank(location, quantity, feature);
            case MUSHROOM_STEW -> new MushroomStewTank(location, quantity, feature);
            case RABBIT_STEW -> new RabbitStewTank(location, quantity, feature);
            case BEETROOT_SOUP -> new BeetrootSoupTank(location, quantity, feature);
            case DRAGON_BREATH -> new DragonBreathTank(location, quantity, feature);
            case EXPERIENCE -> new ExperienceTank(location, quantity, feature);
            case HONEY -> new HoneyTank(location, quantity, feature);
            case EMPTY -> new EmptyTank(location, feature);
        };
    }

    private void registerTank(AbstractTank tank, boolean refreshViewers) {
        TankPosition position = TankPosition.of(tank.getLocation());
        AbstractTank previous = tankIndex.put(position, tank);
        if (previous != null && previous != tank) {
            if (previous instanceof ExperienceTank) {
                experienceTankCount--;
            }
            detachTank(previous);
        }
        if (previous != tank && tank instanceof ExperienceTank) {
            experienceTankCount++;
        }
        if (refreshViewers) {
            refreshTankViewers(tank);
        }
    }

    private boolean unregisterTank(AbstractTank tank) {
        if (!tankIndex.remove(TankPosition.of(tank.getLocation()), tank)) {
            return false;
        }
        if (tank instanceof ExperienceTank) {
            experienceTankCount--;
        }
        detachTank(tank);
        return true;
    }

    private void detachTank(AbstractTank tank) {
        visibility.removeTank(tank);
        tank.clear();
    }

    private void refreshTankViewers(AbstractTank tank) {
        // Callers register exactly one tank at a time. Use Paper's entity spatial index instead
        // of touching every online player when a tank is placed or changes type.
        Location location = tank.getLocation();
        for (Player player : location.getWorld().getNearbyPlayers(
                location,
                VIEW_DISTANCE_BLOCKS
        )) {
            refreshPlayerView(player);
        }
    }

    private void refreshAllPlayerViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerView(player);
        }
    }

    private static Location blockLocation(Location location) {
        return Objects.requireNonNull(location, "location").toBlockLocation();
    }

    private static boolean isTankChunkSent(Player player, AbstractTank tank) {
        Location location = tank.getLocation();
        return player.isChunkSent(Chunk.getChunkKey(
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4
        ));
    }
}
