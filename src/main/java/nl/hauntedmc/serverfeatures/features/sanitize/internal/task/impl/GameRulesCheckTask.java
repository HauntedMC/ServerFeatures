package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Checks all known GameRules in all worlds and warns if a value differs from its default.
 * This task NEVER modifies game rules.
 * <p>
 * Notes:
 * - Uses World#getGameRuleDefault(GameRule) if available (Paper/modern Bukkit).
 * - If the default retrieval API is missing, the task will report "skipped" gracefully.
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
        // Reflect GameRule constants (covers core + newer rules on modern servers)
        List<GameRule<?>> rules = collectAllGameRules();

        // Try to resolve reflection handles (works across Spigot/Paper variants)
        Method getValue = safeMethod(World.class, "getGameRuleValue", GameRule.class);
        Method getDefault = safeMethod(World.class, "getGameRuleDefault", GameRule.class);

        if (getValue == null) {
            return SanitizeResult.unchanged("Skipped — World#getGameRuleValue(GameRule) not available.");
        }
        if (getDefault == null) {
            return SanitizeResult.unchanged("Skipped — server API does not expose game rule defaults.");
        }

        int worldsChecked = 0;
        int mismatches = 0;

        for (World world : Bukkit.getServer().getWorlds()) {
            worldsChecked++;
            for (GameRule<?> rule : rules) {
                try {
                    Object cur = getValue.invoke(world, rule);
                    Object def = getDefault.invoke(world, rule);
                    if (cur == null || def == null) continue;

                    if (!Objects.equals(cur, def)) {
                        mismatches++;
                        String msg = "[GameRulesCheck] World '" + world.getName() + "': gamerule '" +
                                rule.getName() + "' = " + toDisplay(cur) + " (default " + toDisplay(def) + ")";
                        logger.info(msg);
                    }
                } catch (Throwable ignored) {
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

    private static Method safeMethod(Class<?> type, String name, Class<?>... params) {
        try {
            Method m = type.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<GameRule<?>> collectAllGameRules() {
        List<GameRule<?>> out = new ArrayList<>();
        for (Field f : GameRule.class.getDeclaredFields()) {
            try {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!GameRule.class.isAssignableFrom(f.getType())) continue;
                Object v = f.get(null);
                if (v instanceof GameRule<?> gr) out.add(gr);
            } catch (Throwable ignored) {
            }
        }
        // If reflection didn’t find any (very unlikely), fall back to the string names available from a world
        if (out.isEmpty()) {
            World any = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (any != null) {
                try {
                    String[] names = any.getGameRules();
                    for (String n : names) {
                        GameRule<?> gr = GameRule.getByName(n);
                        if (gr != null) out.add(gr);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // Keep a stable order by rule name
        out.sort(Comparator.comparing(GameRule::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String toDisplay(Object v) {
        return String.valueOf(v);
    }
}
