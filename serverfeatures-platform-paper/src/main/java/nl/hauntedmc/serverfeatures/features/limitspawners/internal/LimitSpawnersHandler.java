package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Exact per-spawner lifetime tracking for direct spawner output.
 *
 * <p>The entity PDC is the durable source attribution. The JSON registry additionally remembers
 * unloaded entities, because an unloaded entity is still alive but cannot be resolved through
 * {@link Bukkit#getEntity(UUID)}.</p>
 */
public final class LimitSpawnersHandler {

    private static final String SOURCE_MARKER = "limitspawners_source_v2";

    private final LimitSpawners feature;
    private final int maxSpawn;
    private final int saveIntervalTicks;
    private final int reconcileIntervalTicks;
    private final NamespacedKey sourceMarker;
    private final SpawnerMobRegistry registry = new SpawnerMobRegistry();
    private final SpawnerMobStore store;
    private final Set<UUID> unloadingEntities = new HashSet<>();

    private boolean dirty;

    public LimitSpawnersHandler(LimitSpawners feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
        var config = feature.getConfigHandler();
        this.maxSpawn = Math.max(0, config.node("max_spawn").as(Integer.class, 1));
        this.saveIntervalTicks = Math.max(
                20,
                config.node("save_interval_ticks").as(Integer.class, 100)
        );
        this.reconcileIntervalTicks = Math.max(
                20,
                config.node("reconcile_interval_ticks").as(Integer.class, 200)
        );
        this.sourceMarker = new NamespacedKey(feature.getPlugin(), SOURCE_MARKER);

        Path registryFile = feature.getLifecycleManager()
                .getCacheManager()
                .getCacheDirectory(feature.getFeatureName(), "registry")
                .getDirectory()
                .toPath()
                .resolve("tracked-mobs.json");
        this.store = new SpawnerMobStore(registryFile, feature.getLogger());
        this.registry.load(store.load());
    }

    public void start() {
        int persistedCount = registry.size();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                handleChunkLoad(chunk);
            }
        }
        flushIfDirty();

        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::reconcileLoadedEntities,
                BukkitTime.ticks(reconcileIntervalTicks),
                BukkitTime.ticks(reconcileIntervalTicks)
        );
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::flushIfDirty,
                BukkitTime.ticks(saveIntervalTicks),
                BukkitTime.ticks(saveIntervalTicks)
        );

        feature.getLogger().info(
                "Loaded " + persistedCount + " persisted tracked mobs; "
                        + registry.size() + " remain after loaded-chunk reconciliation."
        );
    }

    public void shutdown() {
        reconcileLoadedEntities();
        flushIfDirty();
        unloadingEntities.clear();
    }

    /**
     * Registers a direct spawner spawn before the event completes, reserving its slot immediately.
     * A MONITOR listener rolls this registration back when another plugin later cancels the event.
     */
    public boolean tryRegisterSpawn(LivingEntity entity, SpawnerKey spawner) {
        UUID entityId = entity.getUniqueId();
        Optional<TrackedSpawnerMob> existing = registry.get(entityId);
        if (existing.isPresent()) {
            ensureSourceMarker(entity, existing.get().spawner());
            updateTrackedLocation(entity);
            return existing.get().spawner().equals(spawner);
        }

        if (registry.count(spawner) >= maxSpawn) {
            return false;
        }

        registerEntity(entity, spawner);
        return true;
    }

    public void rollbackCancelledSpawn(Entity entity) {
        unregister(entity, true);
    }

    public void unregisterIfTracked(Entity entity) {
        unregister(entity, true);
    }

    public void scheduleRemovalCheck(UUID entityId) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> handleRemovalCheck(entityId),
                BukkitTime.ticks(2)
        );
    }

    public void transferTracking(Entity original, List<Entity> transformedEntities) {
        Optional<SpawnerKey> source = registry.get(original.getUniqueId())
                .map(TrackedSpawnerMob::spawner)
                .or(() -> readSourceMarker(original));
        if (source.isEmpty()) {
            return;
        }

        unregister(original, true);
        for (Entity transformed : transformedEntities) {
            if (transformed instanceof LivingEntity living) {
                registerEntity(living, source.get());
            }
        }
    }

    public void updateTrackedLocation(Entity entity) {
        updateTrackedLocation(entity, entity.getLocation());
    }

    public void updateTrackedLocation(Entity entity, Location destination) {
        EntityChunkKey destinationChunk = EntityChunkKey.of(destination);
        registry.get(entity.getUniqueId()).ifPresent(record -> {
            TrackedSpawnerMob relocated = record.relocate(destinationChunk);
            if (!relocated.equals(record)) {
                registry.put(relocated);
                dirty = true;
            }
        });
    }

    public void handleChunkLoad(Chunk chunk) {
        EntityChunkKey chunkKey = EntityChunkKey.of(chunk);
        Map<UUID, LivingEntity> loadedLiving = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                loadedLiving.put(entity.getUniqueId(), living);
            }
        }

        for (UUID expectedId : registry.entityIdsInChunk(chunkKey)) {
            LivingEntity loaded = loadedLiving.get(expectedId);
            if (loaded != null) {
                registry.get(expectedId).ifPresent(record -> {
                    ensureSourceMarker(loaded, record.spawner());
                    updateTrackedLocation(loaded);
                });
                continue;
            }

            Entity resolved = Bukkit.getEntity(expectedId);
            if (resolved instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                updateTrackedLocation(living);
            } else {
                removeById(expectedId, null, false);
            }
        }

        for (LivingEntity entity : loadedLiving.values()) {
            readSourceMarker(entity).ifPresent(source -> registerEntity(entity, source));
            unloadingEntities.remove(entity.getUniqueId());
        }
    }

    public void handleChunkUnload(Chunk chunk) {
        Set<UUID> unloadingNow = new HashSet<>();
        for (Entity entity : chunk.getEntities()) {
            if (!registry.contains(entity.getUniqueId())) {
                continue;
            }
            updateTrackedLocation(entity);
            unloadingNow.add(entity.getUniqueId());
        }
        markTemporarilyUnloading(unloadingNow);
        flushIfDirty();
    }

    public void handleWorldUnload(World world) {
        Set<UUID> unloadingNow = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (!registry.contains(entity.getUniqueId())) {
                continue;
            }
            updateTrackedLocation(entity);
            unloadingNow.add(entity.getUniqueId());
        }
        markTemporarilyUnloading(unloadingNow);
        flushIfDirty();
    }

    /**
     * Includes loaded and unloaded tracked mobs. Unloaded entities remain alive in saved chunk data.
     */
    public int currentAliveCount(SpawnerKey spawner) {
        return registry.count(spawner);
    }

    private void handleRemovalCheck(UUID entityId) {
        if (unloadingEntities.contains(entityId)) {
            return;
        }

        Entity resolved = Bukkit.getEntity(entityId);
        if (resolved instanceof LivingEntity living && !living.isDead() && living.isValid()) {
            readSourceMarker(living).ifPresentOrElse(
                    source -> registerEntity(living, source),
                    () -> unregister(living, false)
            );
            return;
        }

        removeById(entityId, null, false);
    }

    private void registerEntity(LivingEntity entity, SpawnerKey spawner) {
        ensureSourceMarker(entity, spawner);
        EntityChunkKey chunk = EntityChunkKey.of(entity.getLocation());
        TrackedSpawnerMob next = new TrackedSpawnerMob(
                entity.getUniqueId(),
                spawner,
                chunk.worldId(),
                chunk.x(),
                chunk.z()
        );
        TrackedSpawnerMob previous = registry.put(next);
        if (!next.equals(previous)) {
            dirty = true;
        }
    }

    private void unregister(Entity entity, boolean clearMarker) {
        removeById(entity.getUniqueId(), entity, clearMarker);
    }

    private void removeById(UUID entityId, Entity entity, boolean clearMarker) {
        if (registry.remove(entityId).isPresent()) {
            dirty = true;
        }
        unloadingEntities.remove(entityId);
        if (clearMarker && entity != null) {
            entity.getPersistentDataContainer().remove(sourceMarker);
        }
    }

    private void reconcileLoadedEntities() {
        for (TrackedSpawnerMob record : registry.snapshot()) {
            Entity entity = Bukkit.getEntity(record.entityId());
            if (entity == null) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                removeById(record.entityId(), entity, true);
                continue;
            }

            ensureSourceMarker(living, record.spawner());
            updateTrackedLocation(living);
        }
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
            entity.getPersistentDataContainer().remove(sourceMarker);
            feature.getLogger().warning(
                    "Removed invalid spawner source marker from entity " + entity.getUniqueId()
            );
        }
        return parsed;
    }

    private void ensureSourceMarker(Entity entity, SpawnerKey spawner) {
        String expected = spawner.toString();
        String current = entity.getPersistentDataContainer().get(
                sourceMarker,
                PersistentDataType.STRING
        );
        if (!expected.equals(current)) {
            entity.getPersistentDataContainer().set(
                    sourceMarker,
                    PersistentDataType.STRING,
                    expected
            );
        }
    }

    private void markTemporarilyUnloading(Set<UUID> entityIds) {
        if (entityIds.isEmpty()) {
            return;
        }
        unloadingEntities.addAll(entityIds);
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> unloadingEntities.removeAll(entityIds),
                BukkitTime.ticks(20)
        );
    }

    private void flushIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            store.save(registry.snapshot());
            dirty = false;
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.SEVERE,
                    "Could not save tracked mob registry; the next save interval will retry.",
                    exception
            );
        }
    }
}
