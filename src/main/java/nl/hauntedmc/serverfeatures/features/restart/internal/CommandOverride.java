package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class CommandOverride {

    private CommandOverride() {}

    public static void unregisterVanillaRestart(Server server, FeatureLogger logger) {
        try {
            CommandMap map = obtainCommandMap(server);
            Map<String, Command> known = obtainKnownCommands(map);
            if (known == null) return;

            Set<String> toRemove = known.keySet().stream()
                    .filter(CommandOverride::isRestartKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (String key : toRemove) {
                Command cmd = known.get(key);
                if (cmd != null && isDefaultRestart(cmd)) {
                    cmd.unregister(map);
                    known.remove(key);
                }
            }

            syncCommands(server, logger);
        } catch (Throwable t) {
            logger.warning("Could not unregister default /restart: " + t.getMessage());
        }
    }

    /**
     * Hard-takeover of all restart labels (restart, spigot:restart, etc) by your command.
     * Call this AFTER you register your own RestartCommand with the CommandMap.
     */
    public static void takeoverRestart(Server server, FeatureLogger logger, Command yourRestartCommand, String pluginNamespace) {
        try {
            CommandMap map = obtainCommandMap(server);
            Map<String, Command> known = obtainKnownCommands(map);
            if (known == null) return;

            String ns = (pluginNamespace == null ? "serverfeatures" : pluginNamespace).toLowerCase(Locale.ROOT);

            // Collect all keys that look like restart commands (any namespace), plus canonical ones
            Set<String> restartKeys = new LinkedHashSet<>();
            restartKeys.add("restart");
            restartKeys.add("bukkit:restart");
            restartKeys.add("spigot:restart");
            restartKeys.add("paper:restart");
            restartKeys.add("minecraft:restart");
            restartKeys.add(ns + ":restart");

            for (String key : known.keySet()) {
                if (isRestartKey(key)) restartKeys.add(key);
            }

            // Unregister any existing mapping that's not ours, then bind ALL to ours
            for (String key : restartKeys) {
                Command existing = known.get(key);
                if (existing != null && existing != yourRestartCommand) {
                    try { existing.unregister(map); } catch (Throwable ignored) {}
                    known.remove(key);
                }
            }
            for (String key : restartKeys) {
                known.put(key, yourRestartCommand);
            }

            syncCommands(server, logger);
        } catch (Throwable t) {
            logger.warning("Could not takeover /restart: " + t.getMessage());
        }
    }

    private static boolean isRestartKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("restart") || k.endsWith(":restart");
    }

    private static void syncCommands(Server server, FeatureLogger logger) {
        try {
            Method m = server.getClass().getMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(server);
        } catch (Throwable t) {
            logger.warning("Could not sync commands after rebind: " + t.getMessage());
        }
    }

    private static CommandMap obtainCommandMap(Server server) throws Exception {
        Method m = server.getClass().getMethod("getCommandMap");
        m.setAccessible(true);
        return (CommandMap) m.invoke(server);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> obtainKnownCommands(CommandMap map) throws Exception {
        Field f = findField(map.getClass(), "knownCommands");
        if (f == null) return null;
        f.setAccessible(true);
        return (Map<String, Command>) f.get(map);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isDefaultRestart(Command cmd) {
        String n = cmd.getClass().getName();
        if (n.equals("org.bukkit.command.defaults.RestartCommand")) return true;
        if (n.equals("io.papermc.paper.command.builtin.RestartCommand")) return true;
        return n.startsWith("net.minecraft.");
    }
}
