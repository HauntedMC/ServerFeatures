package nl.hauntedmc.serverfeatures.features.votereward.listener;

import com.vexsoftware.votifier.model.VotifierEvent;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class VoteListener implements Listener {

    private final VoteReward feature;

    public VoteListener(VoteReward feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVotifierEvent(VotifierEvent event) {
        feature.getVoteHandler().handleVote(event.getVote());
    }
}
