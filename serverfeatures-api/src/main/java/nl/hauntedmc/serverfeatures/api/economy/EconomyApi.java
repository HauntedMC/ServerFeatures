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

    Optional<EconomyCurrency> currency(String currencyId);

    Collection<EconomyCurrency> currencies();

    String format(String currencyId, java.math.BigDecimal amount);
}
