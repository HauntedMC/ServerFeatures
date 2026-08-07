package nl.hauntedmc.serverfeatures.framework.service;

/** Internal hook for a capability that exposes its publication generation in a DTO. */
public interface CapabilityProviderGenerationAware {
    void providerGeneration(long generation);
}
