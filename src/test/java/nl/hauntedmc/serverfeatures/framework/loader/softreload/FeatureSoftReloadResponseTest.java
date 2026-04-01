package nl.hauntedmc.serverfeatures.framework.loader.softreload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSoftReloadResponseTest {

    @Test
    void successIsTrueOnlyForSuccessResult() {
        FeatureSoftReloadResponse ok = new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, "a");
        FeatureSoftReloadResponse fail = new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, "a");

        assertTrue(ok.success());
        assertFalse(fail.success());
        assertEquals(FeatureSoftReloadResult.NOT_LOADED, fail.result());
        assertEquals("a", fail.feature());
    }
}
