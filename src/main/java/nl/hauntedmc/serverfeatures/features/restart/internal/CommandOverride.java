package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.internal.FeatureLogger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

public final class CommandOverride {

    private CommandOverride() {}

    public static void unregisterVanillaRestart(Server server, FeatureLogger logger) {
        try {
            CommandMap map = obtainCommandMap(server);
            Map<String, Command> known = obtainKnownCommands(map);
            if (map == null || known == null) return;

            String[] keys = {"restart", "bukkit:restart", "minecraft:restart", "spigot:restart", "paper:restart"};
            for (String key : keys) {
                Command cmd = known.get(key);
                if (cmd == null) continue;
                if (isDefaultRestart(cmd)) {
                    cmd.unregister(map);
                    known.remove(key);
                }
            }

            Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Command> e = it.next();
                if (isDefaultRestart(e.getValue())) {
                    e.getValue().unregister(map);
                    it.remove();
                }
            }
        } catch (Throwable t) {
            logger.warning("Could not unregister default /restart: " + t.getMessage());
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