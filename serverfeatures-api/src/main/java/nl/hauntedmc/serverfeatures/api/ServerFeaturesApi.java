package nl.hauntedmc.serverfeatures.api;

import nl.hauntedmc.serverfeatures.api.feature.FeatureCatalog;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRegistry;

import java.util.concurrent.CompletionStage;

/**
 * Stable entry point published by the running ServerFeatures Paper platform.
 *
 * <p>The entry point remains valid for the lifetime of the plugin. Feature reloads change the
 * availability reported by {@link #capabilities()} and {@link #features()}, not this object.</p>
 *
 * <p>See {@link AsyncContract} for threading, callback, cancellation, timeout, and unload rules
 * that apply to public asynchronous capabilities.</p>
 */
public interface ServerFeaturesApi {
    /** Returns the version of the API and runtime implementation. */
    ServerFeaturesApiVersion version();

    /** Current root-runtime lifecycle state. */
    RuntimeState state();

    /** Completes once the initial feature graph is ready. */
    CompletionStage<Void> whenReady();

    /** Returns the live, read-only capability catalog. */
    CapabilityRegistry capabilities();

    /** Returns the live, read-only feature catalog. */
    FeatureCatalog features();
}
