package nl.hauntedmc.serverfeatures.framework.loader.disable;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureDisableResponseTest {

    @Test
    void successIsTrueOnlyForSuccessResult() {
        FeatureDisableResponse ok = new FeatureDisableResponse(FeatureDisableResult.SUCCESS, "a", Set.of("b"));
        FeatureDisableResponse fail = new FeatureDisableResponse(FeatureDisableResult.FAILED, "a", Set.of());

        assertTrue(ok.success());
        assertFalse(fail.success());
        assertEquals(FeatureDisableResult.FAILED, fail.result());
        assertEquals("a", fail.feature());
        assertEquals(Set.of(), fail.alsoDisabledDependents());
    }
}
