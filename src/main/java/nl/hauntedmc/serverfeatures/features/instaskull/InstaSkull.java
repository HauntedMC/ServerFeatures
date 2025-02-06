package nl.hauntedmc.serverfeatures.features.instaskull;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class InstaSkull extends BaseFeature<Meta> {

    public InstaSkull(JavaPlugin plugin) {
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
        if (!configHandler.getBoolean("enabled", false)) return;

        int delay = (int) configHandler.getSetting("delay");
        String message = configHandler.getSetting("message").toString();

        lifecycleManager.registerListener(new PlayerJoinListener());

        lifecycleManager.getTaskManager().scheduleAsyncRepeatingTask(
                () -> Bukkit.broadcastMessage("[SYNC] " + message), delay, 60L);
    }

    private static class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            event.getPlayer().sendMessage("Welcome! InstaSkull feature is active.");
        }
    }
}
