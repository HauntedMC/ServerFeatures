package nl.hauntedmc.serverfeatures.features.nickname.listener;

import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final Nickname feature;

    public PlayerJoinListener(Nickname feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        feature.getNicknameHandler().loadNicknameIntoCache(player);
    }
}
