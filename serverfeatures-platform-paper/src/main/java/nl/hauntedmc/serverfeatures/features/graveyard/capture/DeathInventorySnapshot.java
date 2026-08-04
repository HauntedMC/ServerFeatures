package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public record DeathInventorySnapshot(
        UUID playerId,
        PlayerInventoryState inventory,
        int totalExperience,
        Location deathLocation,
        long capturedAtMillis
) {
    public static DeathInventorySnapshot capture(Player player) {
        return new DeathInventorySnapshot(
                player.getUniqueId(),
                PlayerInventoryState.capture(player),
                Math.max(0, player.calculateTotalExperiencePoints()),
                player.getLocation().clone(),
                System.currentTimeMillis()
        );
    }
}
