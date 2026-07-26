package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Checks all known GameRules in all worlds and warns if a value differs from its default.
 * This task NEVER modifies game rules.
 * <p>
 * Uses the typed game-rule API from the project's supported Paper version.
 */
public class GameRulesCheckTask implements SanitizeTask {

    private final FeatureLogger logger;

    public GameRulesCheckTask(FeatureLogger logger) {
        this.logger = logger;
    }

    @Override
    public String name() {
        return "GameRulesCheck";
    }

    @Override
    public SanitizeResult run(SanitizeContext ctx) {
        List<GameRule<?>> rules = collectAllGameRules();

        int worldsChecked = 0;
        int mismatches = 0;

        for (World world : Bukkit.getServer().getWorlds()) {
            worldsChecked++;
            for (GameRule<?> rule : rules) {
                try {
                    Object cur = world.getGameRuleValue(rule);
                    Object def = rule.getDefaultValue();
                    if (cur == null || def == null) continue;

                    if (!Objects.equals(cur, def)) {
                        mismatches++;
                        String msg = "[GameRulesCheck] World '" + world.getName() + "': gamerule '" +
                                rule.key().value() + "' = " + toDisplay(cur) + " (default " + toDisplay(def) + ")";
                        logger.info(msg);
                    }
                } catch (RuntimeException ignored) {
                    // A rule can be unavailable when its required world feature is disabled.
                }
            }
        }

        if (mismatches == 0) {
            return SanitizeResult.unchanged("All gamerules on " + worldsChecked + " world(s) are at default values.");
        }
        return SanitizeResult.unchanged("Gamerule differences found: " + mismatches +
                " across " + worldsChecked + " world(s). ");
    }

    /* ---------------- helpers ---------------- */

    private static List<GameRule<?>> collectAllGameRules() {
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.GAME_RULE)
                .stream()
                .sorted(Comparator.comparing(rule -> rule.key().value(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String toDisplay(Object v) {
        return String.valueOf(v);
    }
}
