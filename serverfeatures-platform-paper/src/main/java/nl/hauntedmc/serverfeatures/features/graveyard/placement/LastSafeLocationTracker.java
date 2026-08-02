package nl.hauntedmc.serverfeatures.features.graveyard.placement;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LastSafeLocationTracker {
    private final GraveyardSettings settings;
    private final Map<UUID, SafeLocation> locations = new ConcurrentHashMap<>();

    public LastSafeLocationTracker(Graveyard feature, GraveyardSettings settings) {
        this.settings = settings;
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::sampleOnlinePlayers,
                BukkitTime.ticks(20L),
                BukkitTime.ticks(20L)
        );
    }

    public Optional<Location> find(Player player) {
        SafeLocation safe = locations.get(player.getUniqueId());
        if (safe == null || System.currentTimeMillis() - safe.capturedAtMillis() > settings.lastSafeMaxAgeMillis()) {
            return Optional.empty();
        }
        Location location = safe.location();
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
            return Optional.empty();
        }
        return Optional.of(location.clone());
    }

    public void remove(UUID playerId) {
        locations.remove(playerId);
    }

    private void sampleOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isSafe(player)) {
                locations.put(
                        player.getUniqueId(),
                        new SafeLocation(player.getLocation().clone(), System.currentTimeMillis())
                );
            }
        }
    }

    private boolean isSafe(Player player) {
        if (player.isDead()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.isSwimming()) {
            return false;
        }
        Location feet = player.getLocation();
        Block feetBlock = feet.getBlock();
        Block headBlock = feet.clone().add(0.0, 1.0, 0.0).getBlock();
        Block support = feet.clone().add(0.0, -0.1, 0.0).getBlock();
        return feetBlock.isPassable()
                && headBlock.isPassable()
                && support.getType().isSolid()
                && !isHazard(feetBlock.getType())
                && !isHazard(headBlock.getType())
                && !isHazard(support.getType());
    }

    static boolean isHazard(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, CACTUS, MAGMA_BLOCK, POWDER_SNOW,
                    NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
    }

    private record SafeLocation(Location location, long capturedAtMillis) {
        private SafeLocation {
            location = location.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }
}
