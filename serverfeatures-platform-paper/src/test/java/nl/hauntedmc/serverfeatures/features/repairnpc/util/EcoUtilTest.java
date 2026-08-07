package nl.hauntedmc.serverfeatures.features.repairnpc.util;

import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoUtilTest {

    @Test
    void onlyTreatsAnExplicitVaultSuccessAsAPaidRepair() {
        EconomyResponse success = new EconomyResponse(5.0D, 10.0D,
                EconomyResponse.ResponseType.SUCCESS, "");
        EconomyResponse failure = new EconomyResponse(0.0D, 10.0D,
                EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        assertTrue(EcoUtil.successful(success));
        assertFalse(EcoUtil.successful(failure));
        assertFalse(EcoUtil.successful(null));
    }
}
