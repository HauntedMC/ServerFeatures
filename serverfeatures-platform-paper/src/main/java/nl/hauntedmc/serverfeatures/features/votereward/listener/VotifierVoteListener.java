package nl.hauntedmc.serverfeatures.features.votereward.listener;

import com.vexsoftware.votifier.model.VotifierEvent;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.features.votereward.internal.IncomingVote;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class VotifierVoteListener implements Listener {
    private final VoteReward feature;

    public VotifierVoteListener(VoteReward feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVotifierEvent(VotifierEvent event) {
        var v = event.getVote();
        String tsStr = v.getTimeStamp();
        long ts;
        try {
            ts = Long.parseLong(tsStr);
        } catch (Exception ignored) {
            ts = System.currentTimeMillis();
        }
        feature.getVoteHandler().handleVote(new IncomingVote(
                v.getServiceName(),
                v.getUsername(),
                v.getAddress(),
                ts
        ));
    }
}
