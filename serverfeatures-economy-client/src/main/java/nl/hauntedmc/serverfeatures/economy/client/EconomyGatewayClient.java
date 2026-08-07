package nl.hauntedmc.serverfeatures.economy.client;

import nl.hauntedmc.serverfeatures.api.economy.EconomyGatewayChargeRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowResult;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Official cross-process Economy surface for Java services such as the HauntedMC website. */
public interface EconomyGatewayClient {
    CompletionStage<EconomyWorkflowResult> chargeAndDispatch(EconomyGatewayChargeRequest request);

    CompletionStage<Optional<EconomyWorkflowResult>> workflow(String workflowId);
}
