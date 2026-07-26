package nl.hauntedmc.serverfeatures.features.votereward.listener;

import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class VoteJoinListener implements Listener {

    private final VoteReward feature;

    public VoteJoinListener(VoteReward feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.getVoteHandler().processOfflineVotesOnJoin(event.getPlayer());
    }
}
