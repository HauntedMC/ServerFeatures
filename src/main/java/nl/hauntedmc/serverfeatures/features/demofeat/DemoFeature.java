package nl.hauntedmc.serverfeatures.features.demofeat;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.demofeat.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemoFeature extends BaseFeature<Meta>{

    public DemoFeature(JavaPlugin plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("message", "Test completed");
        defaults.put("delay", 20);
        return defaults;
    }

    @Override
    public void initialize() {
        int delay = (int) configHandler.getSetting("delay");
        String message = configHandler.getSetting("message").toString();

        lifecycleManager.registerListener(new PlayerJoinListener());
        lifecycleManager.getCommandManager().registerCommand("demo", new DemoFeatureCommand(), new DemoTabCompleter());

        lifecycleManager.getTaskManager().scheduleDelayedTask(
                () -> Bukkit.broadcastMessage("[SYNC] " + message), delay);
    }

    private static class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            event.getPlayer().sendMessage("Welcome! Demo feature is active.");
        }
    }


    private static class DemoFeatureCommand implements CommandExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
            commandSender.sendMessage("You have executed the Demo command :)");
            return false;
        }
    }

    private static class DemoTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
            if (args.length == 1) {
                return Arrays.asList("test1", "test2", "test3");
            }
            return List.of();
        }
    }
}
