package nl.hauntedmc.serverfeatures.features.commandlogger.listener;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.entity.Player;

import nl.hauntedmc.serverfeatures.features.commandlogger.CommandLogger;

import java.lang.reflect.Method;
import java.util.Locale;

public class CommandListener implements Listener {

    private final CommandLogger feature;

    public CommandListener(CommandLogger feature) {
        this.feature = feature;
    }

    // Players
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String full = stripLeadingSlash(event.getMessage());
        handle(full, event.getPlayer());
    }

    // Console
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String full = stripLeadingSlash(event.getCommand());
        handle(full, event.getSender());
    }

    // RCON
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        String full = stripLeadingSlash(event.getCommand());
        handle(full, event.getSender());
    }

    private void handle(String full, CommandSender source) {
        String trimmed = full.stripLeading();
        if (trimmed.isEmpty()) return;

        String alias = trimmed.split("\\s+", 2)[0];

        // Only log if the command exists and the sender has permission
        CommandMap map = getCommandMap();
        if (map == null) return;

        Command cmd = map.getCommand(alias);
        if (cmd == null) return;
        if (!cmd.testPermissionSilent(source)) return;

        // Who
        String who;
        if (source instanceof Player p) {
            who = p.getName() + " (" + p.getUniqueId() + ")";
        } else {
            who = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }

        // Persist into DB (server read from global config)
        feature.getCommandLogService().logServerCommand(source, full);
    }

    private static String stripLeadingSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static CommandMap getCommandMap() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object cm = m.invoke(Bukkit.getServer());
            if (cm instanceof CommandMap) return (CommandMap) cm;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
