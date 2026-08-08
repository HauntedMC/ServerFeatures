package nl.hauntedmc.serverfeatures.api.service;

import java.util.Set;

/** Read-only catalog of feature capabilities provided by the current runtime. */
public interface CapabilityRegistry {
    <T> CapabilityRef<T> reference(Class<T> type);
    Set<Class<?>> availableTypes();
    AutoCloseable subscribe(CapabilityListener listener);
}
