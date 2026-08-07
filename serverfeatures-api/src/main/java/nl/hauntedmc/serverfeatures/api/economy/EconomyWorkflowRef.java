package nl.hauntedmc.serverfeatures.api.economy;

/** Stable business-operation identity for a durable Economy workflow. */
public record EconomyWorkflowRef(String source, String workflowId) {
    public EconomyWorkflowRef {
        source = EconomyRequestValidation.source(source);
        workflowId = EconomyRequestValidation.text(workflowId, "workflowId", 160, true);
    }
}
