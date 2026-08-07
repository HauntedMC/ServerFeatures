package nl.hauntedmc.serverfeatures.economy.gateway;

import java.util.Objects;
import java.util.Set;

/** Authenticated source identity; the gateway, not the caller, chooses the Economy source name. */
public record EconomyGatewayPrincipal(String source, Set<EconomyGatewayCapability> capabilities) {
    public EconomyGatewayPrincipal {
        source = Objects.requireNonNull(source, "source").trim().toLowerCase(java.util.Locale.ROOT);
        if (!source.matches("[a-z0-9][a-z0-9_.:-]{0,63}")) {
            throw new IllegalArgumentException("source contains unsupported characters");
        }
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean permits(EconomyGatewayCapability capability) {
        return capabilities.contains(capability);
    }
}
