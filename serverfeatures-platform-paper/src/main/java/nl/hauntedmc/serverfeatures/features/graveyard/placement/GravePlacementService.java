package nl.hauntedmc.serverfeatures.features.graveyard.placement;

import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Searches only already-loaded chunks and validates a practical nearby interaction position.
 */
public final class GravePlacementService {
    private final GraveyardSettings settings;
    private final LastSafeLocationTracker safeLocationTracker;

    public GravePlacementService(
            GraveyardSettings settings,
            LastSafeLocationTracker safeLocationTracker
    ) {
        this.settings = settings;
        this.safeLocationTracker = safeLocationTracker;
    }

    public GravePlacementResult place(Player player, Location deathLocation) {
        Optional<Location> exact = validate(normalize(deathLocation));
        if (exact.isPresent()) {
            return result(exact.get(), GravePlacementType.DEATH_LOCATION);
        }

        for (Location candidate : nearbyCandidates(deathLocation)) {
            Optional<Location> validated = validate(candidate);
            if (validated.isPresent()) {
                return result(validated.get(), GravePlacementType.NEARBY);
            }
        }

        Optional<Location> lastSafe = safeLocationTracker.find(player).flatMap(this::validate);
        if (lastSafe.isPresent()) {
            return result(lastSafe.get(), GravePlacementType.LAST_SAFE);
        }

        Optional<Location> surface = surfaceCandidate(deathLocation).flatMap(this::validate);
        if (surface.isPresent()) {
            return result(surface.get(), GravePlacementType.SURFACE);
        }

        return result(normalize(deathLocation), GravePlacementType.REMOTE_ONLY);
    }

    public boolean isReachable(GraveLocation graveLocation) {
        return graveLocation.resolve().flatMap(this::validate).isPresent();
    }

    public Optional<GravePlacementResult> validateRelocation(Location requested) {
        return validate(normalize(requested))
                .map(location -> result(location, GravePlacementType.NEARBY));
    }

    private List<Location> nearbyCandidates(Location origin) {
        List<Location> candidates = new ArrayList<>();
        for (int radius = 0; radius <= settings.horizontalSearchRadius(); radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
                        continue;
                    }
                    for (int y = -settings.verticalSearchBelow(); y <= settings.verticalSearchAbove(); y++) {
                        candidates.add(normalize(origin.clone().add(x, y, z)));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(origin::distanceSquared));
        return candidates;
    }

    private Optional<Location> surfaceCandidate(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        int blockX = origin.getBlockX();
        int blockZ = origin.getBlockZ();
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return Optional.empty();
        }
        Block highest = world.getHighestBlockAt(blockX, blockZ);
        return Optional.of(new Location(
                world,
                blockX + 0.5,
                highest.getY() + 1.0,
                blockZ + 0.5,
                origin.getYaw(),
                0.0f
        ));
    }

    private Optional<Location> validate(Location candidate) {
        World world = candidate.getWorld();
        if (world == null
                || !world.isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)
                || candidate.getY() <= world.getMinHeight()
                || candidate.getY() + 2.0 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(candidate)) {
            return Optional.empty();
        }
        Block feet = candidate.getBlock();
        Block head = candidate.clone().add(0.0, 1.0, 0.0).getBlock();
        Block support = candidate.clone().add(0.0, -0.1, 0.0).getBlock();
        if (!feet.isPassable()
                || !head.isPassable()
                || !support.getType().isSolid()
                || LastSafeLocationTracker.isHazard(feet.getType())
                || LastSafeLocationTracker.isHazard(head.getType())
                || LastSafeLocationTracker.isHazard(support.getType())) {
            return Optional.empty();
        }
        if (!hasInteractionPosition(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate.clone());
    }

    private boolean hasInteractionPosition(Location grave) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Location stand = grave.clone().add(x, 0.0, z);
                World world = stand.getWorld();
                if (world == null || !world.isChunkLoaded(stand.getBlockX() >> 4, stand.getBlockZ() >> 4)) {
                    continue;
                }
                Block feet = stand.getBlock();
                Block head = stand.clone().add(0.0, 1.0, 0.0).getBlock();
                Block support = stand.clone().add(0.0, -0.1, 0.0).getBlock();
                if (feet.isPassable()
                        && head.isPassable()
                        && support.getType().isSolid()
                        && !LastSafeLocationTracker.isHazard(feet.getType())
                        && !LastSafeLocationTracker.isHazard(head.getType())
                        && !LastSafeLocationTracker.isHazard(support.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private GravePlacementResult result(Location location, GravePlacementType type) {
        return new GravePlacementResult(GraveLocation.from(location), type);
    }

    private static Location normalize(Location source) {
        return new Location(
                source.getWorld(),
                source.getBlockX() + 0.5,
                Math.floor(source.getY()),
                source.getBlockZ() + 0.5,
                source.getYaw(),
                0.0f
        );
    }
}
