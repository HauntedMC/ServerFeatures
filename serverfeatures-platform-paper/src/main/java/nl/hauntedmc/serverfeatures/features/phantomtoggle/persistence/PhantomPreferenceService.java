package nl.hauntedmc.serverfeatures.features.phantomtoggle.persistence;

import nl.hauntedmc.serverfeatures.features.phantomtoggle.PhantomToggle;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * Stores each player's PhantomToggle preference in local player data.
 */
public final class PhantomPreferenceService {

    static final NamespacedKey PHANTOMS_ENABLED_KEY =
            new NamespacedKey("serverfeatures", "phantomtoggle_phantoms_enabled");
    private static final byte FALSE = 0;
    private static final byte TRUE = 1;

    private final boolean defaultPhantomsEnabled;

    public PhantomPreferenceService(boolean defaultPhantomsEnabled) {
        this.defaultPhantomsEnabled = defaultPhantomsEnabled;
    }

    /**
     * Returns whether vanilla insomnia phantoms may spawn for this player.
     * Players without the feature permission always keep vanilla behavior.
     */
    public boolean phantomsEnabled(Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.hasPermission(PhantomToggle.USE_PERMISSION)) {
            return true;
        }

        Byte stored = readStoredPreference(player);
        return stored == null ? defaultPhantomsEnabled : stored == TRUE;
    }

    public void setPhantomsEnabled(Player player, boolean enabled) {
        Objects.requireNonNull(player, "player");
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(PHANTOMS_ENABLED_KEY);
        data.set(PHANTOMS_ENABLED_KEY, PersistentDataType.BYTE, enabled ? TRUE : FALSE);
    }

    public boolean shouldSuppressSpawn(Player player) {
        return !phantomsEnabled(player);
    }

    private Byte readStoredPreference(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        try {
            Byte value = data.get(PHANTOMS_ENABLED_KEY, PersistentDataType.BYTE);
            if (value == null || value == FALSE || value == TRUE) {
                return value;
            }
            data.remove(PHANTOMS_ENABLED_KEY);
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
