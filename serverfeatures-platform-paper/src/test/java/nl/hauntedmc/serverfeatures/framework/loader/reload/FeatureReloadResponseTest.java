package nl.hauntedmc.serverfeatures.framework.loader.reload;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureReloadResponseTest {

    @Test
    void successIsTrueOnlyForSuccessResult() {
        FeatureReloadResponse ok = new FeatureReloadResponse(FeatureReloadResult.SUCCESS, "a", Set.of("b"));
        FeatureReloadResponse fail = new FeatureReloadResponse(FeatureReloadResult.FAILED, "a", Set.of());

        assertTrue(ok.success());
        assertFalse(fail.success());
        assertEquals(FeatureReloadResult.FAILED, fail.result());
        assertEquals("a", fail.feature());
        assertEquals(Set.of(), fail.reloadedDependents());
    }
}
