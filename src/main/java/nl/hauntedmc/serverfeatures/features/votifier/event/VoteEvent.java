package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class VoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VotePayload vote;

    public VoteEvent(VotePayload vote) {
        this.vote = vote;
    }

    public VotePayload getVote() {
        return vote;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
