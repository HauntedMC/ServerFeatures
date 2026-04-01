package nl.hauntedmc.serverfeatures.api.ui.inventory.menu;

import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureGUIManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.logging.Logger;

public final class MenuTestSupport {

    private MenuTestSupport() {
    }

    public static FeatureGUIManager guiManager() {
        Plugin plugin = InterfaceProxy.of(Plugin.class, Map.of(
                "getLogger", args -> Logger.getLogger("menu-test")
        ));
        FeatureTaskManager tasks = new FeatureTaskManager(plugin);
        return new FeatureGUIManager(plugin, tasks);
    }
}
