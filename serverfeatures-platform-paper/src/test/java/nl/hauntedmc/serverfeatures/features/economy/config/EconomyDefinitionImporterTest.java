package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.io.config.YamlFile;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.DiscoveredCurrencyDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyDefinitionImporterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void importsGroupMonetaryPolicyWithoutEnablingPlayerPayments() throws Exception {
        ConfigView config = config();
        DiscoveredCurrencyDefinition definition = groupDefinition();

        assertTrue(EconomyDefinitionImporter.preview(config, definition).ready());
        assertEquals(EconomyDefinitionImporter.ImportStatus.IMPORTED,
                EconomyDefinitionImporter.apply(config, definition).status());

        assertEquals("GROUP", config.get("currencies.survival_tokens.definition.scope.type", String.class));
        assertEquals("survival", config.get("currencies.survival_tokens.definition.scope.group_key", String.class));
        assertEquals("HALF_EVEN", config.get("currencies.survival_tokens.definition.balances.rounding", String.class));
        assertEquals("0.01", config.get("currencies.survival_tokens.payments.minimum", String.class));
        assertFalse(config.get("currencies.survival_tokens.commands.pay", Boolean.class));
        assertEquals(EconomyDefinitionImporter.ImportStatus.LOCAL_CURRENCY_EXISTS,
                EconomyDefinitionImporter.preview(config, definition).status());
    }

    @Test
    void refusesLegacyDefinitionWithoutCanonicalPayload() throws Exception {
        ConfigView config = config();
        DiscoveredCurrencyDefinition legacy = new DiscoveredCurrencyDefinition("survival_tokens",
                new EconomyScope(EconomyScopeType.GROUP, "hauntedmc/group/survival"), null, 1L, 1L);

        assertEquals(EconomyDefinitionImporter.ImportStatus.LEGACY_DEFINITION,
                EconomyDefinitionImporter.preview(config, legacy).status());
    }

    private ConfigView config() throws Exception {
        Path file = temporaryDirectory.resolve("economy.yml");
        Files.createFile(file);
        ConfigView config = new ConfigView(new YamlFile(file, Logger.getLogger("economy-import-test")), "");
        config.put("currencies", Map.of());
        return config;
    }

    private static DiscoveredCurrencyDefinition groupDefinition() {
        CurrencyDefinition policy = new CurrencyDefinition("survival_tokens",
                new EconomyScope(EconomyScopeType.GROUP, "hauntedmc/group/survival"), 2,
                new BigDecimal("4.00"), new BigDecimal("-5.00"), new BigDecimal("1000.00"), true,
                RoundingMode.HALF_EVEN);
        return new DiscoveredCurrencyDefinition(policy.currencyId(), policy.scope(), policy, 1L, 1L);
    }
}
