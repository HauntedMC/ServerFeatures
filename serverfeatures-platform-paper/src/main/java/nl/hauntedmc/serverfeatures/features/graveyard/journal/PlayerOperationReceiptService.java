package nl.hauntedmc.serverfeatures.features.graveyard.journal;

import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public final class PlayerOperationReceiptService {
    private final NamespacedKey captureKey;
    private final NamespacedKey claimKey;

    public PlayerOperationReceiptService(Graveyard feature) {
        captureKey = new NamespacedKey(feature.getPlugin(), "graveyard_capture_receipt");
        claimKey = new NamespacedKey(feature.getPlugin(), "graveyard_claim_receipt");
    }

    public void putCapture(Player player, UUID operationToken, UUID graveId) {
        player.getPersistentDataContainer().set(
                captureKey,
                PersistentDataType.STRING,
                operationToken + ":" + graveId
        );
    }

    public void putClaim(Player player, UUID operationToken, UUID graveId) {
        player.getPersistentDataContainer().set(
                claimKey,
                PersistentDataType.STRING,
                operationToken + ":" + graveId
        );
    }

    public Optional<Receipt> capture(Player player) {
        return read(player, captureKey);
    }

    public Optional<Receipt> claim(Player player) {
        return read(player, claimKey);
    }

    public void clearCapture(Player player) {
        player.getPersistentDataContainer().remove(captureKey);
    }

    public void clearClaim(Player player) {
        player.getPersistentDataContainer().remove(claimKey);
    }

    private Optional<Receipt> read(Player player, NamespacedKey key) {
        String value = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Receipt(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record Receipt(UUID operationToken, UUID graveId) {
    }
}
