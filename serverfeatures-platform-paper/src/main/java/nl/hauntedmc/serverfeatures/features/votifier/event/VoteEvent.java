package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class VoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VotePayload vote;
    private CompletionStage<Void> processingCompletion = CompletableFuture.completedFuture(null);

    public VoteEvent(VotePayload vote) {
        this.vote = vote;
    }

    public VotePayload getVote() {
        return vote;
    }

    /**
     * Registers asynchronous business work that must finish before a durable vote can be acknowledged.
     */
    public synchronized void track(CompletionStage<?> processing) {
        Objects.requireNonNull(processing, "processing");
        processingCompletion = processingCompletion.thenCombine(processing, (ignored, result) -> null);
    }

    public synchronized CompletionStage<Void> processingCompletion() {
        return processingCompletion;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
