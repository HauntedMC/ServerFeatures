package nl.hauntedmc.serverfeatures.api.economy;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Native asynchronous multi-currency economy API for trusted in-process integrations.
 *
 * <p>Mutation callers must assign one globally stable source name and persist an
 * idempotency key before dispatching each logical operation. Every retry, including a
 * retry after an unknown timeout or connection result, must reuse that key and the
 * exact same request. Callers must wait for the mutation result instead of treating a
 * preceding balance read as a reservation.</p>
 *
 * <p>Cross-process actors must call a trusted service which delegates to this API (or
 * implements the same locked, journaled transaction). They must never update Economy
 * tables directly.</p>
 */
public interface EconomyApi {

    CompletionStage<EconomyBalance> balance(EconomyAccountRef account);

    Optional<EconomyBalance> cachedBalance(EconomyAccountRef account);

    CompletionStage<EconomyResult> deposit(EconomyMutationRequest request);

    CompletionStage<EconomyResult> withdraw(EconomyMutationRequest request);

    CompletionStage<EconomyResult> setBalance(EconomyMutationRequest request);

    CompletionStage<EconomyResult> transfer(EconomyTransferRequest request);

    /**
     * Atomically debits an account and appends an at-least-once fulfilment event to Economy's
     * durable outbox. Use this for purchases instead of manually withdrawing and then writing a
     * separate domain record.
     */
    CompletionStage<EconomyWorkflowResult> chargeAndDispatch(EconomyWorkflowRequest request);

    /** Returns the durable fulfilment status for a previously submitted workflow. */
    CompletionStage<Optional<EconomyWorkflowResult>> workflow(EconomyWorkflowRef workflow);

    /**
     * Registers one idempotent in-process fulfilment handler for an Economy workflow event type.
     * The handler can run more than once after a process failure and must use the event ID or
     * workflow reference as its domain-side idempotency key.
     */
    EconomyWorkflowRegistration registerWorkflowHandler(String eventType, EconomyWorkflowHandler handler);

    Optional<EconomyCurrency> currency(String currencyId);

    Collection<EconomyCurrency> currencies();

    String format(String currencyId, java.math.BigDecimal amount);
}
