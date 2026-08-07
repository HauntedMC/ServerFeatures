package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyCommandSupportTest {

    @Test
    void temporaryFailureDoesNotExposeInfrastructureDetails() {
        EconomyResult result = new EconomyResult(EconomyResultStatus.TEMPORARY_FAILURE, null, null, null,
                "Communications link failure: database.internal:3306");

        assertEquals("The economy service is temporarily unavailable", EconomyCommandSupport.resultMessage(result));
    }

    @Test
    void rejectsOversizedAmountsBeforeDecimalParsing() {
        assertThrows(IllegalArgumentException.class,
                () -> EconomyCommandSupport.requireSupportedAmountLength("9".repeat(40), false));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyCommandSupport.requireSupportedAmountLength("-" + "9".repeat(40), true));
    }
}
