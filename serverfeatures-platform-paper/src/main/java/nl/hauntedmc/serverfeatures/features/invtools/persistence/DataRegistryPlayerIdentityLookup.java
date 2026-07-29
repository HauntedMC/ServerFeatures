package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * Uses DataRegistry as the network-wide authority for forwarded player UUIDs and current names.
 */
final class DataRegistryPlayerIdentityLookup implements CanonicalPlayerIdentityLookup {

    private final PlayerIdentityResolver resolver;

    DataRegistryPlayerIdentityLookup(DataRegistryApi dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    DataRegistryPlayerIdentityLookup(PlayerDirectory playerDirectory) {
        this.resolver = new PlayerIdentityResolver(
                Objects.requireNonNull(playerDirectory, "playerDirectory")
        );
    }

    /**
     * Creates the production lookup while the feature is initialized on Paper's main thread.
     * Unit tests without a running Bukkit server retain the local fallback-only implementation.
     */
    static CanonicalPlayerIdentityLookup forServerFeatures() {
        if (Bukkit.getServer() == null) {
            return CanonicalPlayerIdentityLookup.none();
        }
        ServerFeatures plugin = JavaPlugin.getPlugin(ServerFeatures.class);
        DataRegistryApi dataRegistry = plugin.getDataRegistry().orElseThrow(() ->
                new IllegalStateException("DataRegistry is required for InvTools offline access.")
        );
        return new DataRegistryPlayerIdentityLookup(dataRegistry);
    }

    @Override
    public Optional<Identity> find(String identifier) throws IOException {
        try {
            return resolver.findByIdentifier(identifier)
                    .toCompletableFuture()
                    .join()
                    .map(identity -> new Identity(identity.uuid(), identity.username()));
        } catch (CompletionException exception) {
            Throwable cause = rootCause(exception);
            throw new IOException(
                    "Could not resolve the canonical player identity for " + identifier,
                    cause
            );
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Could not resolve the canonical player identity for " + identifier,
                    exception
            );
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }
}
