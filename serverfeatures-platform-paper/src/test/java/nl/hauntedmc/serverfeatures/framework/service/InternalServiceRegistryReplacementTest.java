package nl.hauntedmc.serverfeatures.framework.service;

import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalServiceRegistryReplacementTest {

    @Test
    void ownerCanReplaceInternalServiceWithoutAvailabilityGap() {
        InternalServiceRegistry registry = new InternalServiceRegistry();
        FeatureId owner = FeatureId.of("queue");
        Runnable original = () -> { };
        Runnable replacement = () -> { };
        CapabilityRegistration originalRegistration = registry.register(owner, Runnable.class, original);

        CapabilityRegistration replacementRegistration = registry.replace(owner, Runnable.class, replacement);
        assertSame(replacement, registry.require(Runnable.class));

        originalRegistration.close();
        assertSame(replacement, registry.require(Runnable.class));
        replacementRegistration.close();
    }

    @Test
    void anotherOwnerCannotReplaceInternalService() {
        InternalServiceRegistry registry = new InternalServiceRegistry();
        Runnable original = () -> { };
        registry.register(FeatureId.of("queue"), Runnable.class, original);

        assertThrows(IllegalStateException.class, () -> registry.replace(
                FeatureId.of("other"),
                Runnable.class,
                (Runnable) () -> { }
        ));
        assertSame(original, registry.require(Runnable.class));
    }

    @Test
    void internalContractsMustBeInterfaces() {
        InternalServiceRegistry registry = new InternalServiceRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                FeatureId.of("owner"),
                String.class,
                "value"
        ));
    }
}
