package nl.hauntedmc.serverfeatures.api.economy;

import java.util.concurrent.CompletionStage;

/** Idempotent asynchronous consumer of a committed Economy workflow event. */
@FunctionalInterface
public interface EconomyWorkflowHandler {
    CompletionStage<Void> fulfil(EconomyWorkflowEvent event);
}
