package nl.hauntedmc.serverfeatures.features.votereward.listener;

import nl.hauntedmc.serverfeatures.features.votifier.event.VoteEvent;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.features.votereward.internal.IncomingVote;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class NativeVoteListener implements Listener {
    private final VoteReward feature;
    public NativeVoteListener(VoteReward feature) { this.feature = feature; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHauntedVote(VoteEvent event) {
        var v = event.getVote();
        feature.getVoteHandler().handleVote(new IncomingVote(
                v.getServiceName(),
                v.getUsername(),
                v.getAddress(),
                v.getVoteTimestamp()
        ));
    }
}
