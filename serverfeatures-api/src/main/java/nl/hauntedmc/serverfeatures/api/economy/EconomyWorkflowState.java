package nl.hauntedmc.serverfeatures.api.economy;

/** Durable state of a charge-and-fulfil workflow. */
public enum EconomyWorkflowState {
    /** The debit committed; an idempotent handler still needs to apply the domain effect. */
    PENDING_FULFILMENT,
    /** The registered handler acknowledged the domain effect. */
    DELIVERED,
    /** Automatic delivery exhausted its retry budget and requires operational recovery. */
    DEAD_LETTER
}
