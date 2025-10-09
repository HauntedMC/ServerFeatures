package nl.hauntedmc.serverfeatures.internal.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public class FeatureListenerManager {

    private final ServerFeatures plugin;
    private final List<Listener> registeredListeners = new ArrayList<>();

    public FeatureListenerManager(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an event listener and tracks it for later removal.
     */
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }


    public void unregisterAllListeners() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();
    }

    public int getRegisteredListenerCount() {
        return registeredListeners.size();
    }
}
