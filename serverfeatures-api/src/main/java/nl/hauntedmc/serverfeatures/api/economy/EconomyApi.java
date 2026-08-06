package nl.hauntedmc.serverfeatures.api.economy;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Native asynchronous multi-currency economy API. */
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
