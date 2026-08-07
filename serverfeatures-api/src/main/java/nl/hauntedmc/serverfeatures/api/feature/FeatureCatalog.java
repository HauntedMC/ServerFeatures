package nl.hauntedmc.serverfeatures.api.feature;

import java.util.List;
import java.util.Optional;

/** Read-only catalog of all known built-in features. */
public interface FeatureCatalog {
    Optional<FeatureSnapshot> find(FeatureId id);
    List<FeatureSnapshot> snapshot();
    AutoCloseable subscribe(FeatureCatalogListener listener);
}
