package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.DiscoveredCurrencyDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Generates a conservative local currency configuration from a stored shared monetary definition.
 *
 * <p>Imports never overwrite an existing currency and never reload Economy. Display text and
 * command exposure are local policy, so a generated currency receives a reviewable scaffold with
 * player payments disabled. An administrator must review the saved YAML and restart/reload the
 * feature through the normal rollout procedure.</p>
 */
public final class EconomyDefinitionImporter {
    private EconomyDefinitionImporter() {
    }

    public static ImportPreview preview(ConfigView config, DiscoveredCurrencyDefinition discovered) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(discovered, "discovered");
        if (!discovered.importable()) {
            return new ImportPreview(ImportStatus.LEGACY_DEFINITION,
                    "The stored definition has no canonical payload yet; start a known-good server to backfill it.");
        }
        CurrencyDefinition definition = discovered.definition();
        if (definition.scope().type() != EconomyScopeType.GLOBAL && definition.scope().type() != EconomyScopeType.GROUP) {
            return new ImportPreview(ImportStatus.UNSUPPORTED_SCOPE, "Only GLOBAL and GROUP definitions can be imported.");
        }
        if (definition.scope().type() == EconomyScopeType.GROUP) {
            try {
                groupKey(definition.scope().key());
            } catch (IllegalArgumentException exception) {
                return new ImportPreview(ImportStatus.UNSUPPORTED_SCOPE, exception.getMessage());
            }
        }
        if (definition.currencyId().contains(".")) {
            return new ImportPreview(ImportStatus.UNSUPPORTED_CURRENCY_ID,
                    "Currency IDs containing '.' cannot be written safely through the YAML path API.");
        }
        if (!config.node("currencies").get(definition.currencyId()).isNull()) {
            return new ImportPreview(ImportStatus.LOCAL_CURRENCY_EXISTS,
                    "A local currency with this ID already exists; imports never overwrite it.");
        }
        String conflict = commandConflict(config, definition.currencyId());
        if (conflict != null) {
            return new ImportPreview(ImportStatus.COMMAND_CONFLICT,
                    "The generated command root '" + definition.currencyId() + "' conflicts with " + conflict + ".");
        }
        return new ImportPreview(ImportStatus.READY,
                "Will add currencies." + definition.currencyId() + " with the stored "
                        + definition.scope().type().name().toLowerCase(Locale.ROOT) + " monetary policy. "
                        + "Player payments remain disabled until reviewed locally.");
    }

    /** Writes a previously previewed definition as one atomic config-file update. */
    public static ImportPreview apply(ConfigView config, DiscoveredCurrencyDefinition discovered) {
        ImportPreview preview = preview(config, discovered);
        if (!preview.ready()) {
            return preview;
        }
        CurrencyDefinition definition = discovered.definition();
        String base = "currencies." + definition.currencyId() + ".";
        config.batch(batch -> {
            batch.put(base + "enabled", true);
            batch.put(base + "definition.scope.type", definition.scope().type().name());
            if (definition.scope().type() == EconomyScopeType.GROUP) {
                batch.put(base + "definition.scope.group_key", groupKey(definition.scope().key()));
            }
            batch.put(base + "display.singular", definition.currencyId());
            batch.put(base + "display.plural", definition.currencyId());
            batch.put(base + "display.symbol", "");
            batch.put(base + "display.format", "{symbol}{amount}");
            batch.put(base + "definition.fractional_digits", definition.fractionalDigits());
            batch.put(base + "display.grouping", true);
            batch.put(base + "definition.balances.starting", definition.startingBalance().toPlainString());
            batch.put(base + "definition.balances.minimum", definition.minimumBalance().toPlainString());
            batch.put(base + "definition.balances.maximum", definition.maximumBalance().toPlainString());
            batch.put(base + "definition.balances.allow_negative", definition.allowNegative());
            batch.put(base + "definition.balances.rounding", definition.rounding().name());
            batch.put(base + "commands.root", definition.currencyId());
            batch.put(base + "commands.aliases", List.of());
            batch.put(base + "commands.balance", true);
            batch.put(base + "commands.balance_others", false);
            batch.put(base + "commands.pay", false);
            batch.put(base + "commands.paytoggle", false);
            batch.put(base + "commands.history", true);
            batch.put(base + "commands.top", false);
            batch.put(base + "payments.default_enabled", false);
            batch.put(base + "payments.minimum", BigDecimal.ONE.movePointLeft(definition.fractionalDigits()).toPlainString());
            batch.put(base + "payments.maximum", "0");
            batch.put(base + "payments.confirmation_threshold", "0");
            batch.put(base + "payments.daily_send_limit", "0");
            batch.put(base + "payments.daily_receive_limit", "0");
            batch.put(base + "payments.cooldown", "1s");
        });
        return new ImportPreview(ImportStatus.IMPORTED,
                "Currency configuration was saved. Review display/commands and restart or reload Economy before use.");
    }

    private static String groupKey(String scopeKey) {
        int marker = scopeKey.indexOf("/group/");
        if (marker < 1 || marker + 7 >= scopeKey.length() || scopeKey.indexOf('/', marker + 7) >= 0) {
            throw new IllegalArgumentException("Invalid stored GROUP scope key: " + scopeKey);
        }
        String groupKey = scopeKey.substring(marker + 7);
        String normalized = EconomyConfigValues.key(groupKey, "stored GROUP scope key");
        if (!normalized.equals(groupKey)) {
            throw new IllegalArgumentException("Stored GROUP scope key is not in canonical form: " + scopeKey);
        }
        return groupKey;
    }

    private static String commandConflict(ConfigView config, String root) {
        for (var entry : config.node("currencies").children().entrySet()) {
            var currency = entry.getValue();
            if (!currency.getAt("enabled").as(Boolean.class, true)) {
                continue;
            }
            String configuredRoot = currency.getAt("commands.root").as(String.class, entry.getKey());
            if (root.equalsIgnoreCase(configuredRoot)) {
                return "currency '" + entry.getKey() + "'";
            }
            for (String alias : currency.getAt("commands.aliases").listOf(String.class)) {
                if (root.equalsIgnoreCase(alias)) {
                    return "an alias of currency '" + entry.getKey() + "'";
                }
            }
        }
        return null;
    }

    public enum ImportStatus {
        READY,
        IMPORTED,
        LEGACY_DEFINITION,
        UNSUPPORTED_SCOPE,
        UNSUPPORTED_CURRENCY_ID,
        LOCAL_CURRENCY_EXISTS,
        COMMAND_CONFLICT
    }

    public record ImportPreview(ImportStatus status, String message) {
        public ImportPreview {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }

        public boolean ready() {
            return status == ImportStatus.READY;
        }
    }
}
