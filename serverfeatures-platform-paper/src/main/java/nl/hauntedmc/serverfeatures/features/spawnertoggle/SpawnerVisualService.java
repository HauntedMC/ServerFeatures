package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Sends a client-only disabled spawner state without changing the server-side activation range.
 */
public final class SpawnerVisualService {

    static final int DISABLED_VISUAL_REQUIRED_PLAYER_RANGE = 0;

    private final Predicate<CreatureSpawner> disabledCheck;

    public SpawnerVisualService(Predicate<CreatureSpawner> disabledCheck) {
        this.disabledCheck = Objects.requireNonNull(disabledCheck, "disabledCheck");
    }

    public void refresh(CreatureSpawner spawner) {
        Objects.requireNonNull(spawner, "spawner");
        World world = spawner.getWorld();
        for (Player viewer : world.getPlayers()) {
            refresh(viewer, spawner);
        }
    }

    public void refresh(Player viewer, CreatureSpawner spawner) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spawner, "spawner");
        Chunk chunk = spawner.getChunk();
        if (!viewer.isOnline()
                || !chunk.isLoaded()
                || !viewer.getWorld().equals(spawner.getWorld())
                || !viewer.isChunkSent(chunk)) {
            return;
        }

        if (disabledCheck.test(spawner)) {
            CreatureSpawner visualState = (CreatureSpawner) spawner.copy();
            visualState.setRequiredPlayerRange(DISABLED_VISUAL_REQUIRED_PLAYER_RANGE);
            viewer.sendBlockUpdate(spawner.getLocation(), visualState);
            return;
        }

        viewer.sendBlockUpdate(spawner.getLocation(), spawner);
    }

    public void refreshChunk(Player viewer, Chunk chunk) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(chunk, "chunk");
        if (!viewer.isOnline()
                || !chunk.isLoaded()
                || !viewer.getWorld().equals(chunk.getWorld())
                || !viewer.isChunkSent(chunk)) {
            return;
        }

        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner spawner && disabledCheck.test(spawner)) {
                refresh(viewer, spawner);
            }
        }
    }

    public void restoreActual(CreatureSpawner spawner) {
        Objects.requireNonNull(spawner, "spawner");
        World world = spawner.getWorld();
        Chunk chunk = spawner.getChunk();
        if (!chunk.isLoaded()) {
            return;
        }
        for (Player viewer : world.getPlayers()) {
            if (viewer.isOnline()
                    && viewer.getWorld().equals(world)
                    && viewer.isChunkSent(chunk)) {
                viewer.sendBlockUpdate(spawner.getLocation(), spawner);
            }
        }
    }
}
