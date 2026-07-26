package nl.hauntedmc.serverfeatures.features.bossbar.listener;

import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BossbarListener implements Listener {

    private final Bossbars feature;

    public BossbarListener(Bossbars feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.getBossbarHandler().showBossbar(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        feature.getBossbarHandler().removeBossbar(event.getPlayer());
    }
}
