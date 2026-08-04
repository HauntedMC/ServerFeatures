package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Shared persistent state for disabling a block spawner without abusing activation range semantics.
 */
public final class SpawnerToggleState {

    private static final String DISABLED_KEY = "spawner_toggle_disabled";

    private SpawnerToggleState() {
    }

    public static boolean isDisabled(CreatureSpawner spawner, Plugin plugin) {
        Objects.requireNonNull(spawner, "spawner");
        Byte value = spawner.getPersistentDataContainer().get(
                key(plugin),
                PersistentDataType.BYTE
        );
        return value != null && value == (byte) 1;
    }

    public static void setDisabled(CreatureSpawner spawner, Plugin plugin, boolean disabled) {
        Objects.requireNonNull(spawner, "spawner");
        if (disabled) {
            spawner.getPersistentDataContainer().set(
                    key(plugin),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        } else {
            spawner.getPersistentDataContainer().remove(key(plugin));
        }
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), DISABLED_KEY);
    }
}
