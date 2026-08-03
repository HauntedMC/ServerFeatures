package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.LimitMetric;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.SpawnerToggleState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Performance-first controller for block-spawner density and active spawner mobs.
 *
 * <p>Mob state is deliberately runtime-only. Only low-churn spawner block positions are persisted.</p>
 */
public final class LimitSpawnersHandler {

    private static final String SOURCE_MARKER = "limitspawners_source_v3";
    private static final long SAVE_TIMEOUT_SECONDS = 5L;
    private static final double SPLIT_MATCH_DISTANCE_SQUARED = 16.0D;

    private final LimitSpawners feature;
    private final LimitSpawnersConfig config;
    private final SpawnerMobRegistry mobRegistry = new SpawnerMobRegistry();
    private final SpawnerPositionIndex positionIndex = new SpawnerPositionIndex();
    private final SpawnerPositionStore positionStore;
    private final SpawnerLimitResolver limitResolver;
    private final SpawnerSafetyPolicy safetyPolicy;
    private final NamespacedKey sourceMarker;
    private final Map<UUID, PendingSpawnReservation> pendingSpawns = new HashMap<>();
    private final Map<UUID, PendingTransformReservation> pendingTransforms = new HashMap<>();
    private final Map<SpawnerKey, PendingPlacementReservation> pendingPlacements = new HashMap<>();
    private final Map<UUID, PendingSplit> pendingSplits = new HashMap<>();
    private final Map<SpawnerKey, Long> inactiveSince = new HashMap<>();
    private final EnumMap<LimitMetric, Long> metrics = new EnumMap<>(LimitMetric.class);
    private final AtomicLong positionMutationVersion = new AtomicLong();
    private final AtomicLong persistedPositionVersion = new AtomicLong();
    private final AtomicBoolean positionSaveInProgress = new AtomicBoolean();

    private volatile CompletableFuture<Void> positionSaveFuture = CompletableFuture.completedFuture(null);
    private boolean running;

    public LimitSpawnersHandler(LimitSpawners feature, LimitSpawnersConfig config) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.config = Objects.requireNonNull(config, "config");
        this.limitResolver = new SpawnerLimitResolver(config);
        this.safetyPolicy = new SpawnerSafetyPolicy(config.spawnerSafety());
        this.sourceMarker = new NamespacedKey(feature.getPlugin(), SOURCE_MARKER);

        Path indexFile = feature.getLifecycleManager()
                .getCacheManager()
                .getCacheDirectory(feature.getFeatureName(), "positions")
                .getDirectory()
                .toPath()
                .resolve("spawner-index.json");
        this.positionStore = new SpawnerPositionStore(indexFile, feature.getLogger());
        this.positionIndex.load(positionStore.load());

        for (LimitMetric metric : LimitMetric.values()) {
            metrics.put(metric, 0L);
        }
    }

    public void start() {
        running = true;
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                this::bootstrapLoadedChunks,
                BukkitTime.ticks(1)
        );
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::runMaintenance,
                BukkitTime.ticks(config.mobControl().maintenanceIntervalTicks()),
                BukkitTime.ticks(config.mobControl().maintenanceIntervalTicks())
        );
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::flushPositionsAsyncIfDirty,
                BukkitTime.ticks(config.positionIndex().saveDebounceTicks()),
                BukkitTime.ticks(config.positionIndex().saveDebounceTicks())
        );
    }

    public void shutdown() {
        running = false;
        finalizePendingSpawns();
        rollbackPendingTransforms();
        pendingPlacements.clear();
        pendingSplits.clear();

        for (TrackedSpawnerMob record : mobRegistry.snapshot()) {
            cleanupEntity(record.entityId(), LimitMetric.FEATURE_DISABLE, true);
        }
        mobRegistry.clear();
        inactiveSince.clear();

        awaitPendingPositionSave();
        flushPositionsSynchronously();
    }

    public LimitSpawnersConfig config() {
        return config;
    }

    public SpawnDecision tryReserveSpawnerSpawn(
            LivingEntity entity,
            CreatureSpawner spawner
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(spawner, "spawner");
        if (!running) {
            return SpawnDecision.DISABLED_SOURCE;
        }

        safetyPolicy.apply(spawner);
        SpawnerKey source = SpawnerKey.of(spawner.getLocation());
        addPosition(source);

        if (isDisabled(spawner)) {
            increment(LimitMetric.SPAWN_BLOCKED_DISABLED_SOURCE);
            return SpawnDecision.DISABLED_SOURCE;
        }

        SpawnDecision decision = evaluateAddition(source, entity.getType(), 1, 0);
        if (!decision.allowed()) {
            increment(decision.metric());
            return decision;
        }

        registerPendingEntity(entity, source, null);
        return SpawnDecision.ALLOWED;
    }

    public void recordSpawnerMinecartBlocked() {
        increment(LimitMetric.SPAWNER_MINECART_BLOCKED);
    }

    public void applyBlockedRetry(CreatureSpawner spawner) {
        int retryDelay = config.mobControl().blockedRetryDelayTicks();
        if (spawner.getDelay() < retryDelay) {
            spawner.setDelay(retryDelay);
            spawner.update(true, false);
        }
    }

    public void scheduleSpawnFinalization(Entity entity, BooleanSupplier cancellationState) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(cancellationState, "cancellationState");
        UUID entityId = entity.getUniqueId();
        PendingSpawnReservation pending = pendingSpawns.get(entityId);
        if (pending == null) {
            return;
        }
        pendingSpawns.put(entityId, pending.withCancellationState(cancellationState));
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> finalizeSpawn(entityId),
                BukkitTime.ticks(1)
        );
    }

    public PlacementDecision tryReservePlacement(Player player, SpawnerKey position) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        if (!config.placementControl().enabled()) {
            return new PlacementDecision(true, 0, Integer.MAX_VALUE);
        }

        reconcileLoadedChunksInRadius(position);
        SpawnerLimitResolver.PlacementLimit limit = limitResolver.placementLimit(player);
        int nearby = positionIndex.countWithin(position, config.farmRadius())
                + pendingPlacementCount(position);
        if (!limit.permits(nearby)) {
            increment(LimitMetric.PLACEMENT_BLOCKED);
            return new PlacementDecision(false, nearby, limit.limit());
        }

        pendingPlacements.put(position, new PendingPlacementReservation(null));
        return new PlacementDecision(true, nearby, limit.limit());
    }

    public void schedulePlacementFinalization(
            SpawnerKey position,
            BooleanSupplier cancellationState
    ) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cancellationState, "cancellationState");
        PendingPlacementReservation pending = pendingPlacements.get(position);
        if (pending == null) {
            return;
        }
        pendingPlacements.put(position, pending.withCancellationState(cancellationState));
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> finalizePlacement(position),
                BukkitTime.ticks(1)
        );
    }

    public void handleChunkLoad(Chunk chunk) {
        reconcileSpawnerChunk(chunk);
        removeCrashRecoveredEntities(List.of(chunk.getEntities()));
    }

    public void handleEntitiesLoad(List<Entity> entities) {
        removeCrashRecoveredEntities(entities);
    }

    public void handleChunkUnload(Chunk chunk) {
        EntityChunkKey chunkKey = EntityChunkKey.of(chunk);
        for (SpawnerKey source : mobRegistry.spawnersInSourceChunk(chunkKey)) {
            cleanupSource(source, LimitMetric.SOURCE_CHUNK_UNLOAD);
        }

        for (Entity entity : chunk.getEntities()) {
            if (mobRegistry.contains(entity.getUniqueId())) {
                cleanupEntity(entity.getUniqueId(), LimitMetric.MOB_CHUNK_UNLOAD, true);
            }
        }
    }

    public void handleWorldUnload(World world) {
        UUID worldId = world.getUID();
        for (TrackedSpawnerMob record : mobRegistry.snapshot()) {
            if (record.spawner().worldId().equals(worldId)) {
                cleanupEntity(record.entityId(), LimitMetric.SOURCE_CHUNK_UNLOAD, true);
            }
        }
    }

    public void handleEntityDeath(LivingEntity entity) {
        cleanupEntity(entity.getUniqueId(), LimitMetric.DEATH, false);
    }

    public void handleEntityRemoval(Entity entity, String causeName) {
        if (!mobRegistry.contains(entity.getUniqueId())) {
            return;
        }
        LimitMetric metric = removalMetric(causeName);
        cleanupEntity(entity.getUniqueId(), metric, false);
    }

    public void handleTeleport(Entity entity, Location destination) {
        Optional<TrackedSpawnerMob> tracked = mobRegistry.get(entity.getUniqueId());
        if (tracked.isEmpty()) {
            return;
        }
        World destinationWorld = destination.getWorld();
        if (destinationWorld == null
                || !tracked.get().spawner().worldId().equals(destinationWorld.getUID())) {
            cleanupEntity(entity.getUniqueId(), LimitMetric.CROSS_WORLD_TELEPORT, true);
            return;
        }
        mobRegistry.put(tracked.get().relocate(EntityChunkKey.of(destination)));
    }

    public void scheduleSourceValidation(SpawnerKey source) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> validateSourceBlock(source),
                BukkitTime.ticks(1)
        );
    }

    public void scheduleToggleValidation(SpawnerKey source) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            Optional<CreatureSpawner> spawner = loadedSpawner(source);
            if (spawner.isPresent() && isDisabled(spawner.get())) {
                cleanupSource(source, LimitMetric.SOURCE_DISABLED);
            }
        }, BukkitTime.ticks(1));
    }

    public boolean reserveTransform(Entity original, List<Entity> transformedEntities) {
        Optional<TrackedSpawnerMob> current = mobRegistry.get(original.getUniqueId());
        if (current.isEmpty()) {
            return true;
        }

        List<LivingEntity> living = transformedEntities.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        SpawnDecision decision = evaluateReplacement(current.get().spawner(), living, 1);
        if (!decision.allowed()) {
            increment(decision.metric());
            return false;
        }

        mobRegistry.remove(original.getUniqueId());
        for (LivingEntity replacement : living) {
            registerEntity(replacement, current.get().spawner());
        }
        pendingTransforms.put(
                original.getUniqueId(),
                new PendingTransformReservation(original, current.get(), living, null)
        );
        return true;
    }

    public void scheduleTransformFinalization(
            Entity original,
            BooleanSupplier cancellationState
    ) {
        PendingTransformReservation pending = pendingTransforms.get(original.getUniqueId());
        if (pending == null) {
            return;
        }
        pendingTransforms.put(
                original.getUniqueId(),
                pending.withCancellationState(cancellationState)
        );
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> finalizeTransform(original.getUniqueId()),
                BukkitTime.ticks(1)
        );
    }

    public int prepareSlimeSplit(LivingEntity parent, int requestedChildren) {
        Optional<TrackedSpawnerMob> tracked = mobRegistry.get(parent.getUniqueId());
        if (tracked.isEmpty()) {
            return requestedChildren;
        }

        int available = availableReplacementCapacity(
                tracked.get().spawner(),
                parent.getType(),
                1
        );
        int accepted = Math.max(0, Math.min(requestedChildren, available));
        if (accepted == 0) {
            return 0;
        }

        long expiresAt = System.currentTimeMillis() + 1_000L;
        pendingSplits.put(
                parent.getUniqueId(),
                new PendingSplit(
                        tracked.get().spawner(),
                        parent.getLocation(),
                        accepted,
                        expiresAt
                )
        );
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> pendingSplits.remove(parent.getUniqueId()),
                BukkitTime.ticks(20)
        );
        return accepted;
    }

    public boolean tryReserveSlimeChild(LivingEntity child) {
        Optional<SpawnerKey> source = claimSplitSource(child.getLocation());
        if (source.isEmpty()) {
            return true;
        }
        return tryReserveDescendant(child, source.get(), LimitMetric.SLIME_SPLIT);
    }

    public boolean tryReserveShulkerChild(LivingEntity parent, LivingEntity child) {
        Optional<TrackedSpawnerMob> tracked = mobRegistry.get(parent.getUniqueId());
        if (tracked.isEmpty()) {
            return true;
        }
        return tryReserveDescendant(
                child,
                tracked.get().spawner(),
                LimitMetric.SHULKER_DUPLICATION
        );
    }

    public void cleanupSource(SpawnerKey source, LimitMetric metric) {
        for (UUID entityId : mobRegistry.entityIdsForSpawner(source)) {
            cleanupEntity(entityId, metric, true);
        }
        inactiveSince.remove(source);
    }

    public int cleanupRadius(Location center, int radius) {
        SpawnerKey centerKey = SpawnerKey.of(center);
        long radiusSquared = (long) radius * radius;
        int removed = 0;
        for (SpawnerKey source : mobRegistry.sources()) {
            if (centerKey.distanceSquared(source) <= radiusSquared) {
                removed += cleanupSourceCount(source, LimitMetric.PLUGIN_REMOVAL);
            }
        }
        return removed;
    }

    public int cleanupWorld(UUID worldId) {
        int removed = 0;
        for (SpawnerKey source : mobRegistry.sources()) {
            if (source.worldId().equals(worldId)) {
                removed += cleanupSourceCount(source, LimitMetric.PLUGIN_REMOVAL);
            }
        }
        return removed;
    }

    public int rescanLoadedChunks() {
        int changed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                changed += reconcileSpawnerChunk(chunk);
            }
        }
        return changed;
    }

    public Optional<SpawnerInspection> inspect(SpawnerKey source, Player viewer) {
        World world = Bukkit.getWorld(source.worldId());
        if (world == null || !world.isChunkLoaded(source.chunkX(), source.chunkZ())) {
            return Optional.empty();
        }
        BlockState state = world.getBlockAt(source.x(), source.y(), source.z()).getState();
        if (!(state instanceof CreatureSpawner spawner)) {
            return Optional.empty();
        }

        List<TrackedEntityView> entities = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (UUID entityId : mobRegistry.entityIdsForSpawner(source)) {
            mobRegistry.get(entityId).ifPresent(record -> {
                Entity entity = Bukkit.getEntity(entityId);
                double distance = entity == null
                        ? -1.0D
                        : Math.sqrt(source.distanceSquared(entity.getLocation()));
                entities.add(new TrackedEntityView(
                        entityId,
                        record.entityType(),
                        Math.max(0L, (now - record.spawnedAtMillis()) / 1_000L),
                        distance
                ));
            });
        }

        int placementLimit = viewer == null
                ? config.placementControl().defaultLimit()
                : limitResolver.placementLimit(viewer).limit();
        return Optional.of(new SpawnerInspection(
                source,
                spawner.getSpawnedType(),
                isDisabled(spawner),
                spawner.isActivated(),
                mobRegistry.count(source),
                config.mobControl().perSpawnerLimit(
                        Optional.ofNullable(spawner.getSpawnedType()).orElse(EntityType.PIG)
                ),
                mobRegistry.countInArea(source, config.farmRadius()),
                config.mobControl().perAreaLimit(),
                positionIndex.countWithin(source, config.farmRadius()),
                placementLimit,
                spawner.getDelay(),
                spawner.getMinSpawnDelay(),
                spawner.getMaxSpawnDelay(),
                spawner.getSpawnCount(),
                spawner.getRequiredPlayerRange(),
                spawner.getSpawnRange(),
                spawner.getMaxNearbyEntities(),
                List.copyOf(entities)
        ));
    }

    public StatsSnapshot stats() {
        Map<UUID, Integer> worldCounts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            int count = mobRegistry.worldCount(world.getUID());
            if (count > 0) {
                worldCounts.put(world.getUID(), count);
            }
        }
        return new StatsSnapshot(
                mobRegistry.size(),
                positionIndex.size(),
                Map.copyOf(worldCounts),
                Map.copyOf(metrics)
        );
    }

    public SpawnerLimitResolver.PlacementLimit placementLimit(Player player) {
        return limitResolver.placementLimit(player);
    }

    private void bootstrapLoadedChunks() {
        if (!running) {
            return;
        }
        int changed = rescanLoadedChunks();
        flushPositionsAsyncIfDirty();
        feature.getLogger().info(
                "Loaded " + positionIndex.size() + " indexed spawners; bootstrap reconciled "
                        + changed + " position changes."
        );
    }

    private void runMaintenance() {
        if (!running) {
            return;
        }
        long now = System.currentTimeMillis();
        long radiusSquared = (long) config.farmRadius() * config.farmRadius();

        for (SpawnerKey source : mobRegistry.sources()) {
            Optional<CreatureSpawner> loaded = loadedSpawner(source);
            if (loaded.isEmpty()) {
                cleanupSource(source, LimitMetric.SOURCE_CHUNK_UNLOAD);
                continue;
            }

            CreatureSpawner spawner = loaded.get();
            addPosition(source);
            safetyPolicy.apply(spawner);
            if (isDisabled(spawner)) {
                cleanupSource(source, LimitMetric.SOURCE_DISABLED);
                continue;
            }

            if (!spawner.isActivated()) {
                long inactiveAt = inactiveSince.computeIfAbsent(source, ignored -> now);
                if (elapsedSeconds(inactiveAt, now)
                        >= config.mobControl().inactiveSourceGraceSeconds()) {
                    cleanupSource(source, LimitMetric.INACTIVE_SOURCE);
                    continue;
                }
            } else {
                inactiveSince.remove(source);
            }

            for (UUID entityId : mobRegistry.entityIdsForSpawner(source)) {
                Optional<TrackedSpawnerMob> tracked = mobRegistry.get(entityId);
                if (tracked.isEmpty()) {
                    continue;
                }
                Entity resolved = Bukkit.getEntity(entityId);
                if (!(resolved instanceof LivingEntity living)
                        || living.isDead()
                        || !living.isValid()) {
                    cleanupEntity(entityId, LimitMetric.DESPAWN, false);
                    continue;
                }
                if (!living.getWorld().getUID().equals(source.worldId())) {
                    cleanupEntity(entityId, LimitMetric.CROSS_WORLD_TELEPORT, true);
                    continue;
                }

                TrackedSpawnerMob current = tracked.get().relocate(
                        EntityChunkKey.of(living.getLocation())
                );
                if (source.distanceSquared(living.getLocation()) > radiusSquared) {
                    current = current.outsideSince(now);
                    Long outsideAt = current.outsideSinceMillis();
                    if (outsideAt != null
                            && elapsedSeconds(outsideAt, now)
                            >= config.mobControl().outsideRadiusGraceSeconds()) {
                        cleanupEntity(entityId, LimitMetric.OUTSIDE_RADIUS, true);
                        continue;
                    }
                } else {
                    current = current.clearOutsideSince();
                }

                int maximumLifetime = config.mobControl().maximumLifetimeSeconds();
                if (maximumLifetime > 0
                        && elapsedSeconds(current.spawnedAtMillis(), now) >= maximumLifetime) {
                    cleanupEntity(entityId, LimitMetric.MAXIMUM_LIFETIME, true);
                    continue;
                }
                mobRegistry.put(current);
            }
        }
    }

    private SpawnDecision evaluateAddition(
            SpawnerKey source,
            EntityType entityType,
            int additions,
            int removals
    ) {
        int sourceCount = mobRegistry.count(source) - removals + additions;
        if (sourceCount > config.mobControl().perSpawnerLimit(entityType)) {
            return SpawnDecision.SOURCE_CAP;
        }

        int areaCount = mobRegistry.countInArea(source, config.farmRadius())
                - removals
                + additions;
        if (areaCount > config.mobControl().perAreaLimit()) {
            return SpawnDecision.AREA_CAP;
        }

        int worldCount = mobRegistry.worldCount(source.worldId()) - removals + additions;
        if (worldCount > config.mobControl().perWorldLimit()) {
            return SpawnDecision.WORLD_CAP;
        }

        int serverCount = mobRegistry.size() - removals + additions;
        if (serverCount > config.mobControl().serverLimit()) {
            return SpawnDecision.SERVER_CAP;
        }
        return SpawnDecision.ALLOWED;
    }

    private SpawnDecision evaluateReplacement(
            SpawnerKey source,
            List<LivingEntity> replacements,
            int removals
    ) {
        int additions = replacements.size();
        int finalSourceCount = mobRegistry.count(source) - removals + additions;
        for (LivingEntity replacement : replacements) {
            if (finalSourceCount > config.mobControl().perSpawnerLimit(replacement.getType())) {
                return SpawnDecision.SOURCE_CAP;
            }
        }
        return evaluateAddition(
                source,
                replacements.isEmpty() ? EntityType.PIG : replacements.getFirst().getType(),
                additions,
                removals
        );
    }

    private int availableReplacementCapacity(
            SpawnerKey source,
            EntityType entityType,
            int removals
    ) {
        int sourceAvailable = config.mobControl().perSpawnerLimit(entityType)
                - (mobRegistry.count(source) - removals);
        int areaAvailable = config.mobControl().perAreaLimit()
                - (mobRegistry.countInArea(source, config.farmRadius()) - removals);
        int worldAvailable = config.mobControl().perWorldLimit()
                - (mobRegistry.worldCount(source.worldId()) - removals);
        int serverAvailable = config.mobControl().serverLimit()
                - (mobRegistry.size() - removals);
        return Math.max(
                0,
                Math.min(
                        Math.min(sourceAvailable, areaAvailable),
                        Math.min(worldAvailable, serverAvailable)
                )
        );
    }

    private boolean tryReserveDescendant(
            LivingEntity child,
            SpawnerKey source,
            LimitMetric metric
    ) {
        SpawnDecision decision = evaluateAddition(source, child.getType(), 1, 0);
        if (!decision.allowed()) {
            increment(decision.metric());
            return false;
        }
        registerPendingEntity(child, source, null);
        increment(metric);
        return true;
    }

    private void registerPendingEntity(
            LivingEntity entity,
            SpawnerKey source,
            BooleanSupplier cancellationState
    ) {
        registerEntity(entity, source);
        pendingSpawns.put(
                entity.getUniqueId(),
                new PendingSpawnReservation(entity, source, cancellationState)
        );
    }

    private void registerEntity(LivingEntity entity, SpawnerKey source) {
        ensureSourceMarker(entity, source);
        mobRegistry.put(new TrackedSpawnerMob(
                entity.getUniqueId(),
                source,
                entity.getType(),
                System.currentTimeMillis(),
                EntityChunkKey.of(entity.getLocation()),
                null
        ));
    }

    private void finalizePendingSpawns() {
        for (UUID entityId : List.copyOf(pendingSpawns.keySet())) {
            finalizeSpawn(entityId);
        }
    }

    private void finalizeSpawn(UUID entityId) {
        PendingSpawnReservation pending = pendingSpawns.remove(entityId);
        if (pending == null) {
            return;
        }
        if (isCancelled(pending.cancellationState())) {
            cleanupEntity(entityId, LimitMetric.CANCELLED_SPAWN, false);
            clearSourceMarker(pending.entity());
            return;
        }

        Entity resolved = Bukkit.getEntity(entityId);
        if (!(resolved instanceof LivingEntity living)
                || living.isDead()
                || !living.isValid()) {
            cleanupEntity(entityId, LimitMetric.CANCELLED_SPAWN, false);
            clearSourceMarker(pending.entity());
            return;
        }

        Optional<CreatureSpawner> source = loadedSpawner(pending.source());
        if (source.isEmpty()) {
            cleanupEntity(entityId, LimitMetric.SOURCE_REMOVED, true);
            return;
        }
        if (isDisabled(source.get())) {
            cleanupEntity(entityId, LimitMetric.SOURCE_DISABLED, true);
            return;
        }

        ensureSourceMarker(living, pending.source());
        mobRegistry.get(entityId).ifPresent(record ->
                mobRegistry.put(record.relocate(EntityChunkKey.of(living.getLocation())))
        );
    }

    private void finalizeTransform(UUID originalId) {
        PendingTransformReservation pending = pendingTransforms.remove(originalId);
        if (pending == null) {
            return;
        }

        if (isCancelled(pending.cancellationState())) {
            for (LivingEntity replacement : pending.replacements()) {
                mobRegistry.remove(replacement.getUniqueId());
                clearSourceMarker(replacement);
            }
            if (!pending.original().isDead() && pending.original().isValid()) {
                mobRegistry.put(pending.originalRecord());
                ensureSourceMarker(pending.original(), pending.originalRecord().spawner());
            }
            return;
        }

        clearSourceMarker(pending.original());
        for (LivingEntity replacement : pending.replacements()) {
            Entity resolved = Bukkit.getEntity(replacement.getUniqueId());
            if (!(resolved instanceof LivingEntity living)
                    || living.isDead()
                    || !living.isValid()) {
                mobRegistry.remove(replacement.getUniqueId());
                clearSourceMarker(replacement);
                continue;
            }
            ensureSourceMarker(living, pending.originalRecord().spawner());
            mobRegistry.get(living.getUniqueId()).ifPresent(record ->
                    mobRegistry.put(record.relocate(EntityChunkKey.of(living.getLocation())))
            );
        }
        increment(LimitMetric.TRANSFORMATION);
    }

    private void rollbackPendingTransforms() {
        for (UUID originalId : List.copyOf(pendingTransforms.keySet())) {
            PendingTransformReservation pending = pendingTransforms.get(originalId);
            if (pending != null) {
                pendingTransforms.put(
                        originalId,
                        pending.withCancellationState(() -> true)
                );
            }
            finalizeTransform(originalId);
        }
    }

    private Optional<SpawnerKey> claimSplitSource(Location childLocation) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingSplit> entry : List.copyOf(pendingSplits.entrySet())) {
            PendingSplit split = entry.getValue();
            if (split.expiresAtMillis() < now) {
                pendingSplits.remove(entry.getKey());
                continue;
            }
            if (!sameWorld(split.location(), childLocation)
                    || split.location().distanceSquared(childLocation)
                    > SPLIT_MATCH_DISTANCE_SQUARED) {
                continue;
            }

            int remaining = split.remainingChildren() - 1;
            if (remaining <= 0) {
                pendingSplits.remove(entry.getKey());
            } else {
                pendingSplits.put(entry.getKey(), split.withRemainingChildren(remaining));
            }
            return Optional.of(split.source());
        }
        return Optional.empty();
    }

    private void finalizePlacement(SpawnerKey position) {
        PendingPlacementReservation pending = pendingPlacements.remove(position);
        if (pending == null || isCancelled(pending.cancellationState())) {
            return;
        }

        Optional<CreatureSpawner> spawner = loadedSpawner(position);
        if (spawner.isEmpty()) {
            return;
        }
        safetyPolicy.apply(spawner.get());
        addPosition(position);
    }

    private int pendingPlacementCount(SpawnerKey center) {
        long radiusSquared = (long) config.farmRadius() * config.farmRadius();
        int count = 0;
        for (SpawnerKey pending : pendingPlacements.keySet()) {
            if (center.distanceSquared(pending) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    private void reconcileLoadedChunksInRadius(SpawnerKey center) {
        World world = Bukkit.getWorld(center.worldId());
        if (world == null) {
            return;
        }
        int chunkRadius = Math.max(1, (config.farmRadius() + 15) >> 4);
        for (int chunkX = center.chunkX() - chunkRadius;
                chunkX <= center.chunkX() + chunkRadius;
                chunkX++) {
            for (int chunkZ = center.chunkZ() - chunkRadius;
                    chunkZ <= center.chunkZ() + chunkRadius;
                    chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    reconcileSpawnerChunk(world.getChunkAt(chunkX, chunkZ));
                }
            }
        }
    }

    private int reconcileSpawnerChunk(Chunk chunk) {
        Set<SpawnerKey> actual = new HashSet<>();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner spawner) {
                SpawnerKey key = SpawnerKey.of(spawner.getLocation());
                actual.add(key);
                safetyPolicy.apply(spawner);
            }
        }

        int changed = 0;
        for (SpawnerKey known : positionIndex.positionsInChunk(
                chunk.getWorld().getUID(),
                chunk.getX(),
                chunk.getZ()
        )) {
            if (!actual.contains(known) && positionIndex.remove(known)) {
                changed++;
                markPositionsDirty();
                cleanupSource(known, LimitMetric.SOURCE_REMOVED);
            }
        }
        for (SpawnerKey discovered : actual) {
            if (positionIndex.add(discovered)) {
                changed++;
                markPositionsDirty();
            }
        }
        return changed;
    }

    private void validateSourceBlock(SpawnerKey source) {
        Optional<CreatureSpawner> spawner = loadedSpawner(source);
        if (spawner.isPresent()) {
            safetyPolicy.apply(spawner.get());
            addPosition(source);
            return;
        }
        if (positionIndex.remove(source)) {
            markPositionsDirty();
        }
        cleanupSource(source, LimitMetric.SOURCE_REMOVED);
    }

    private Optional<CreatureSpawner> loadedSpawner(SpawnerKey source) {
        World world = Bukkit.getWorld(source.worldId());
        if (world == null || !world.isChunkLoaded(source.chunkX(), source.chunkZ())) {
            return Optional.empty();
        }
        Block block = world.getBlockAt(source.x(), source.y(), source.z());
        if (block.getType() != Material.SPAWNER) {
            return Optional.empty();
        }
        BlockState state = block.getState();
        return state instanceof CreatureSpawner spawner
                ? Optional.of(spawner)
                : Optional.empty();
    }

    private boolean isDisabled(CreatureSpawner spawner) {
        return SpawnerToggleState.isDisabled(spawner, feature.getPlugin());
    }

    private void removeCrashRecoveredEntities(List<Entity> entities) {
        for (Entity entity : entities) {
            if (readSourceMarker(entity).isEmpty()) {
                continue;
            }
            clearSourceMarker(entity);
            entity.remove();
            increment(LimitMetric.CRASH_RECOVERY);
        }
    }

    private int cleanupSourceCount(SpawnerKey source, LimitMetric metric) {
        int count = mobRegistry.count(source);
        cleanupSource(source, metric);
        return count;
    }

    private void cleanupEntity(UUID entityId, LimitMetric metric, boolean removeEntity) {
        Optional<TrackedSpawnerMob> removed = mobRegistry.remove(entityId);
        pendingSpawns.remove(entityId);
        if (removed.isEmpty()) {
            return;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            clearSourceMarker(entity);
            if (removeEntity && entity.isValid()) {
                entity.remove();
            }
        }
        increment(metric);
    }

    private Optional<SpawnerKey> readSourceMarker(Entity entity) {
        String serialized = entity.getPersistentDataContainer().get(
                sourceMarker,
                PersistentDataType.STRING
        );
        if (serialized == null) {
            return Optional.empty();
        }
        Optional<SpawnerKey> parsed = SpawnerKey.parse(serialized);
        if (parsed.isEmpty()) {
            clearSourceMarker(entity);
            feature.getLogger().warning(
                    "Removed invalid LimitSpawners marker from entity " + entity.getUniqueId()
            );
        }
        return parsed;
    }

    private void ensureSourceMarker(Entity entity, SpawnerKey source) {
        entity.getPersistentDataContainer().set(
                sourceMarker,
                PersistentDataType.STRING,
                source.toString()
        );
    }

    private void clearSourceMarker(Entity entity) {
        entity.getPersistentDataContainer().remove(sourceMarker);
    }

    private void addPosition(SpawnerKey source) {
        if (positionIndex.add(source)) {
            markPositionsDirty();
        }
    }

    private void markPositionsDirty() {
        positionMutationVersion.incrementAndGet();
    }

    private void flushPositionsAsyncIfDirty() {
        long version = positionMutationVersion.get();
        if (version <= persistedPositionVersion.get()
                || !positionSaveInProgress.compareAndSet(false, true)) {
            return;
        }

        List<SpawnerKey> snapshot = positionIndex.snapshot();
        CompletableFuture<Void> future;
        try {
            future = feature.getLifecycleManager()
                    .getTaskManager()
                    .runAsync(() -> positionStore.save(snapshot));
        } catch (RuntimeException exception) {
            positionSaveInProgress.set(false);
            feature.getLogger().log(
                    Level.SEVERE,
                    "Could not schedule spawner position save; the next interval will retry.",
                    exception
            );
            return;
        }

        positionSaveFuture = future;
        future.whenComplete((ignored, throwable) -> {
            try {
                if (throwable == null) {
                    persistedPositionVersion.accumulateAndGet(version, Math::max);
                } else if (!(unwrap(throwable) instanceof CancellationException)) {
                    feature.getLogger().log(
                            Level.SEVERE,
                            "Could not save spawner position index; the next interval will retry.",
                            unwrap(throwable)
                    );
                }
            } finally {
                positionSaveInProgress.set(false);
            }
        });
    }

    private void awaitPendingPositionSave() {
        CompletableFuture<Void> pending = positionSaveFuture;
        try {
            pending.get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pending.cancel(false);
            feature.getLogger().warning(
                    "Interrupted while waiting for the spawner position save; retrying synchronously."
            );
        } catch (TimeoutException exception) {
            pending.cancel(false);
            feature.getLogger().warning(
                    "Timed out waiting for the spawner position save; retrying synchronously."
            );
        } catch (ExecutionException | CancellationException ignored) {
            // The asynchronous completion handler logs failures. The synchronous flush retries.
        }
    }

    private void flushPositionsSynchronously() {
        long version = positionMutationVersion.get();
        if (version <= persistedPositionVersion.get()) {
            return;
        }
        try {
            positionStore.save(positionIndex.snapshot());
            persistedPositionVersion.set(version);
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.SEVERE,
                    "Could not save the final spawner position index during shutdown.",
                    exception
            );
        }
    }

    private void increment(LimitMetric metric) {
        metrics.merge(metric, 1L, Long::sum);
    }

    private static LimitMetric removalMetric(String causeName) {
        String normalized = causeName == null ? "" : causeName.toUpperCase();
        if (normalized.contains("DEATH")) {
            return LimitMetric.DEATH;
        }
        if (normalized.contains("DESPAWN")) {
            return LimitMetric.DESPAWN;
        }
        if (normalized.contains("UNLOAD")) {
            return LimitMetric.MOB_CHUNK_UNLOAD;
        }
        if (normalized.contains("TRANSFORM")) {
            return LimitMetric.TRANSFORMATION;
        }
        return LimitMetric.PLUGIN_REMOVAL;
    }

    private static boolean isCancelled(BooleanSupplier cancellationState) {
        if (cancellationState == null) {
            return false;
        }
        try {
            return cancellationState.getAsBoolean();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static long elapsedSeconds(long startedAt, long now) {
        return Math.max(0L, now - startedAt) / 1_000L;
    }

    private static boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID());
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completion
                && completion.getCause() != null) {
            return completion.getCause();
        }
        return throwable;
    }

    public enum SpawnDecision {
        ALLOWED(null),
        SOURCE_CAP(LimitMetric.SPAWN_BLOCKED_SOURCE_CAP),
        AREA_CAP(LimitMetric.SPAWN_BLOCKED_AREA_CAP),
        WORLD_CAP(LimitMetric.SPAWN_BLOCKED_WORLD_CAP),
        SERVER_CAP(LimitMetric.SPAWN_BLOCKED_SERVER_CAP),
        DISABLED_SOURCE(LimitMetric.SPAWN_BLOCKED_DISABLED_SOURCE);

        private final LimitMetric metric;

        SpawnDecision(LimitMetric metric) {
            this.metric = metric;
        }

        public boolean allowed() {
            return this == ALLOWED;
        }

        public LimitMetric metric() {
            return metric;
        }
    }

    public record PlacementDecision(boolean allowed, int nearbyCount, int limit) {
    }

    public record StatsSnapshot(
            int activeMobs,
            int indexedSpawners,
            Map<UUID, Integer> worldCounts,
            Map<LimitMetric, Long> metrics
    ) {
    }

    public record TrackedEntityView(
            UUID entityId,
            EntityType entityType,
            long ageSeconds,
            double distanceFromSource
    ) {
    }

    public record SpawnerInspection(
            SpawnerKey source,
            EntityType entityType,
            boolean disabled,
            boolean activated,
            int activeCount,
            int sourceLimit,
            int areaCount,
            int areaLimit,
            int nearbySpawnerCount,
            int placementLimit,
            int delay,
            int minimumDelay,
            int maximumDelay,
            int spawnCount,
            int requiredPlayerRange,
            int spawnRange,
            int maxNearbyEntities,
            List<TrackedEntityView> entities
    ) {
    }

    private record PendingSpawnReservation(
            Entity entity,
            SpawnerKey source,
            BooleanSupplier cancellationState
    ) {
        private PendingSpawnReservation withCancellationState(BooleanSupplier state) {
            return new PendingSpawnReservation(entity, source, Objects.requireNonNull(state, "state"));
        }
    }

    private record PendingPlacementReservation(BooleanSupplier cancellationState) {
        private PendingPlacementReservation withCancellationState(BooleanSupplier state) {
            return new PendingPlacementReservation(Objects.requireNonNull(state, "state"));
        }
    }

    private record PendingTransformReservation(
            Entity original,
            TrackedSpawnerMob originalRecord,
            List<LivingEntity> replacements,
            BooleanSupplier cancellationState
    ) {
        private PendingTransformReservation {
            replacements = List.copyOf(replacements);
        }

        private PendingTransformReservation withCancellationState(BooleanSupplier state) {
            return new PendingTransformReservation(
                    original,
                    originalRecord,
                    replacements,
                    Objects.requireNonNull(state, "state")
            );
        }
    }

    private record PendingSplit(
            SpawnerKey source,
            Location location,
            int remainingChildren,
            long expiresAtMillis
    ) {
        private PendingSplit withRemainingChildren(int remaining) {
            return new PendingSplit(source, location, remaining, expiresAtMillis);
        }
    }
}
