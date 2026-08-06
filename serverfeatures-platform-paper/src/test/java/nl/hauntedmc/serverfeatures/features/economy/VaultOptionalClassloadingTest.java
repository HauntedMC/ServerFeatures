package nl.hauntedmc.serverfeatures.features.economy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VaultOptionalClassloadingTest {

    @Test
    void nativeFeatureEntryPointsDoNotLinkVaultApiClasses() throws IOException {
        assertNoVaultApiReference(Economy.class);
    }

    private static void assertNoVaultApiReference(Class<?> type) throws IOException {
        String resourceName = type.getSimpleName() + ".class";
        try (InputStream stream = type.getResourceAsStream(resourceName)) {
            assertNotNull(stream, () -> "Missing class resource for " + type.getName());
            String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(
                    constantPool.contains("net/milkbowl/vault"),
                    () -> type.getName() + " directly links Vault API classes"
            );
        }
    }
}
