package nl.hauntedmc.serverfeatures.api.economy;

/** Handle returned by a workflow-handler registration. */
@FunctionalInterface
public interface EconomyWorkflowRegistration extends AutoCloseable {
    @Override
    void close();
}
