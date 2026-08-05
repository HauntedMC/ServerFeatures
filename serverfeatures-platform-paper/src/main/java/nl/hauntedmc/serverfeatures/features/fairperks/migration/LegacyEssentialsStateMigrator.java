package nl.hauntedmc.serverfeatures.features.fairperks.migration;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time compatibility bridge for fly and god flags persisted by Essentials.
 *
 * <p>Essentials is never used as the authoritative runtime state. This bridge only reads and clears
 * legacy flags while a player is online so the native FairPerks state can take ownership safely.</p>
 */
public final class LegacyEssentialsStateMigrator {

    private final FairPerks feature;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public LegacyEssentialsStateMigrator(FairPerks feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    public MigrationResult migrate(Player player) {
        Plugin essentials = feature.getPlugin().getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            return MigrationResult.unavailable();
        }

        try {
            Method getUser = essentials.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(essentials, player);
            if (user == null) {
                logFailure("Essentials returned no user for an online player.");
                return MigrationResult.unavailable();
            }
            return migrateUser(user);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure(
                    "Essentials legacy fly/god migration is unavailable: "
                            + exception.getClass().getSimpleName()
            );
            return MigrationResult.unavailable();
        }
    }

    static MigrationResult migrateUser(Object user) throws ReflectiveOperationException {
        Class<?> type = user.getClass();
        Method isFlyEnabled = type.getMethod("isFlyModeEnabled");
        Method isGodEnabled = type.getMethod("isGodModeEnabled");
        Method setFlyEnabled = type.getMethod("setFlyModeEnabled", boolean.class);
        Method setGodEnabled = type.getMethod("setGodModeEnabled", boolean.class);

        boolean flyEnabled = Boolean.TRUE.equals(isFlyEnabled.invoke(user));
        boolean godEnabled = Boolean.TRUE.equals(isGodEnabled.invoke(user));
        if (flyEnabled) {
            setFlyEnabled.invoke(user, false);
        }
        if (godEnabled) {
            setGodEnabled.invoke(user, false);
        }
        return MigrationResult.completed(flyEnabled, godEnabled);
    }

    private void logFailure(String message) {
        if (failureLogged.compareAndSet(false, true)) {
            feature.getLogger().warning(message + " Migration will be retried on a later login.");
        }
    }

    public record MigrationResult(boolean completed, boolean flyEnabled, boolean godEnabled) {

        public static MigrationResult completed(boolean flyEnabled, boolean godEnabled) {
            return new MigrationResult(true, flyEnabled, godEnabled);
        }

        public static MigrationResult unavailable() {
            return new MigrationResult(false, false, false);
        }
    }
}
