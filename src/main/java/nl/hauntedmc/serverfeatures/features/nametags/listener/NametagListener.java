package nl.hauntedmc.serverfeatures.features.nametags.listener;

import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class NametagListener implements Listener {

    private final Nametags feature;

    public NametagListener(Nametags feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        this.feature.updateNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.feature.removeNametag(player);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        this.feature.updateNametag(event.getPlayer());
    }
}