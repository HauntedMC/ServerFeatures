package nl.hauntedmc.serverfeatures.framework.service;

/** Internal idempotent handle for one feature-owned capability registration. */
@FunctionalInterface
public interface CapabilityRegistration extends AutoCloseable {
    @Override
    void close();
}
