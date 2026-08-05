package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.config.AutoPickupSettings;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread player state backed by the player's local PersistentDataContainer.
 */
public final class AutoPickupPreferenceService {

    static final NamespacedKey ENABLED_KEY =
            new NamespacedKey("serverfeatures", "autopickup_enabled");
    private static final byte FALSE = 0;
    private static final byte TRUE = 1;

    private final AutoPickup feature;
    private final AutoPickupSettings settings;
    private final Map<UUID, AutoPickupPlayerState> states = new HashMap<>();

    public AutoPickupPreferenceService(AutoPickup feature, AutoPickupSettings settings) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void initialize(Player player) {
        Objects.requireNonNull(player, "player");
        Byte stored = readStoredPreference(player);
        boolean enabled = stored == null ? settings.defaultEnabled() : stored == TRUE;
        states.put(player.getUniqueId(), new AutoPickupPlayerState(enabled));
    }

    public void remove(Player player) {
        states.remove(player.getUniqueId());
    }

    public boolean isEnabled(Player player) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        return state != null
                && state.enabled()
                && (!settings.requireUsePermission() || player.hasPermission(AutoPickup.USE_PERMISSION));
    }

    public void handleCommand(Player player, CommandIntent intent) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        if (state == null) {
            initialize(player);
            state = states.get(player.getUniqueId());
        }

        if (intent == CommandIntent.STATUS) {
            send(player, state.enabled()
                    ? "autopickup.status.enabled"
                    : "autopickup.status.disabled");
            return;
        }

        boolean desired = switch (intent) {
            case ENABLE -> true;
            case DISABLE -> false;
            case TOGGLE -> !state.enabled();
            case STATUS -> throw new IllegalStateException("STATUS was handled before state mutation");
        };
        if (desired == state.enabled()) {
            // An explicit command repairs/reasserts the stored preference, including after a
            // conservation failure temporarily disabled the current session.
            if (intent == CommandIntent.ENABLE || intent == CommandIntent.DISABLE) {
                writeStoredPreference(player, desired);
            }
            send(player, desired
                    ? "autopickup.already_enabled"
                    : "autopickup.already_disabled");
            return;
        }

        state.enabled(desired);
        writeStoredPreference(player, desired);
        send(player, desired ? "autopickup.enabled" : "autopickup.disabled");
    }

    public boolean shouldNotifyFull(Player player, boolean partialInsertion, long nowNanos) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        AutoPickupSettings.NotificationSettings notification = settings.notification();
        if (state == null || !notification.enabled()) {
            return false;
        }
        if (partialInsertion && !notification.notifyOnPartial()) {
            return false;
        }
        long previousNotice = state.lastFullNoticeNanos();
        if (previousNotice != Long.MIN_VALUE
                && nowNanos - previousNotice < notification.cooldownNanos()) {
            return false;
        }
        state.lastFullNoticeNanos(nowNanos);
        return true;
    }

    public void disableForSession(Player player) {
        AutoPickupPlayerState state = states.get(player.getUniqueId());
        if (state != null) {
            state.enabled(false);
        }
    }

    public void close() {
        states.clear();
    }

    private Byte readStoredPreference(Player player) {
        try {
            Byte value = player.getPersistentDataContainer().get(
                    ENABLED_KEY,
                    PersistentDataType.BYTE
            );
            if (value == null || value == FALSE || value == TRUE) {
                return value;
            }
            player.getPersistentDataContainer().remove(ENABLED_KEY);
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void writeStoredPreference(Player player, boolean enabled) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(ENABLED_KEY);
        data.set(ENABLED_KEY, PersistentDataType.BYTE, enabled ? TRUE : FALSE);
    }

    private void send(Player player, String key) {
        feature.sendPlayerMessage(player, key);
    }
}
