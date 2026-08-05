package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@FunctionalInterface
public interface CombatStatusProvider {

    CombatStatus status(Player player);

    static CombatStatusProvider resolve(FairPerks feature) {
        Objects.requireNonNull(feature, "feature");
        Plugin plugin = feature.getPlugin().getServer().getPluginManager().getPlugin("CombatLogX");
        if (plugin == null || !plugin.isEnabled()) {
            return player -> CombatStatus.UNAVAILABLE;
        }

        try {
            Method combatManagerMethod = plugin.getClass().getMethod("getCombatManager");
            Object combatManager = combatManagerMethod.invoke(plugin);
            if (combatManager == null) {
                feature.getLogger().warning("CombatLogX returned no combat manager; activation checks will use the configured fallback.");
                return player -> CombatStatus.UNAVAILABLE;
            }
            Method isInCombatMethod = combatManager.getClass().getMethod("isInCombat", Player.class);
            AtomicBoolean failureLogged = new AtomicBoolean();
            return player -> {
                try {
                    return Boolean.TRUE.equals(isInCombatMethod.invoke(combatManager, player))
                            ? CombatStatus.IN_COMBAT
                            : CombatStatus.NOT_IN_COMBAT;
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    if (failureLogged.compareAndSet(false, true)) {
                        feature.getLogger().warning(
                                "CombatLogX combat checks failed; activation checks will use the configured fallback: "
                                        + exception.getClass().getSimpleName()
                        );
                    }
                    return CombatStatus.UNAVAILABLE;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException exception) {
            feature.getLogger().warning(
                    "CombatLogX API is incompatible; activation checks will use the configured fallback: "
                            + exception.getClass().getSimpleName()
            );
            return player -> CombatStatus.UNAVAILABLE;
        }
    }

    enum CombatStatus {
        IN_COMBAT,
        NOT_IN_COMBAT,
        UNAVAILABLE
    }
}
