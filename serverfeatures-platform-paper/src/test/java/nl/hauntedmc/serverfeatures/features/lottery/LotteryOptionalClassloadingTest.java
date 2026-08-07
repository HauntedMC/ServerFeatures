package nl.hauntedmc.serverfeatures.features.lottery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LotteryOptionalClassloadingTest {

    @Test
    void featureEntryPointDoesNotLinkVaultApiClasses() throws IOException {
        String resourceName = Lottery.class.getSimpleName() + ".class";
        try (InputStream stream = Lottery.class.getResourceAsStream(resourceName)) {
            assertNotNull(stream, () -> "Missing class resource for " + Lottery.class.getName());
            String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(
                    constantPool.contains("net/milkbowl/vault"),
                    () -> Lottery.class.getName() + " directly links Vault API classes"
            );
        }
    }
}
