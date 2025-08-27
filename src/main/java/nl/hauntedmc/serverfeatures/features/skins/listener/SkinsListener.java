package nl.hauntedmc.serverfeatures.features.skins.listener;

import nl.hauntedmc.serverfeatures.features.skins.Skins;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class SkinsListener implements Listener {

    private final Skins feature;

    public SkinsListener(Skins feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var uuid = e.getPlayer().getUniqueId();
        feature.getState().markCustomSkin(uuid, false);
        feature.getState().clearLastUse(uuid);
    }
}
