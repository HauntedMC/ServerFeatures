package nl.hauntedmc.serverfeatures.features.autopickup.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaTest {

    @Test
    void hasNoFeatureOrPluginDependencies() {
        Meta meta = new Meta();

        assertTrue(meta.getDependencies().isEmpty());
        assertTrue(meta.getPluginDependencies().isEmpty());
    }
}
