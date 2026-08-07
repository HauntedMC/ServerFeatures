package nl.hauntedmc.serverfeatures.features.economy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cache-only PlaceholderAPI expansion for Economy account and currency presentation values.
 *
 * <p>Placeholder reads never resolve identities or query MySQL. Account values are available only
 * while the player is cached on this Paper server; {@code available} distinguishes an unavailable
 * cache entry from an actual zero balance. Currency metadata remains available without a player.</p>
 */
public final class EconomyPlaceholder extends PlaceholderExpansion {
    private static final String PRIMARY_PREFIX = "primary_";
    private static final int MAX_PARAMS_LENGTH = 128;
    private static final List<String> SUFFIXES = List.of(
            "fractional_digits", "scope_type", "available", "payments", "frozen", "status",
            "balance", "currency", "singular", "plural", "symbol", "scope", "raw"
    );

    private final Economy feature;

    public EconomyPlaceholder(Economy feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "economy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HauntedMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        EconomySettings settings = feature.settings();
        PlaceholderRequest request = PlaceholderRequest.parse(params, settings);
        if (request == null) {
            return null;
        }
        EconomyService service = feature.service();
        if (isCurrencyMetadata(request.suffix())) {
            return render(request, Optional.empty(), service);
        }
        Optional<Account> account = service == null || player == null
                ? Optional.empty()
                : service.cachedAccount(player.getUniqueId(), request.currency().id());
        return render(request, account, service);
    }

    private String render(PlaceholderRequest request, Optional<Account> account, EconomyService service) {
        EconomySettings.Currency currency = request.currency();
        return switch (request.suffix()) {
            case "balance" -> account.map(value -> service.format(currency.id(), value.balance())).orElse("0");
            case "raw" -> account.map(value -> value.balance().toPlainString()).orElse("0");
            case "available" -> Boolean.toString(account.isPresent());
            case "payments" -> account.map(value -> Boolean.toString(value.paymentsEnabled()))
                    .orElse(Boolean.toString(currency.payments().defaultEnabled()));
            case "frozen" -> account.map(value -> Boolean.toString(value.status() == AccountStatus.FROZEN))
                    .orElse("false");
            case "status" -> account.map(value -> value.status().name().toLowerCase(Locale.ROOT)).orElse("unavailable");
            case "scope" -> currency.scope().key();
            case "scope_type" -> currency.scope().type().name().toLowerCase(Locale.ROOT);
            case "currency" -> currency.id();
            case "symbol" -> currency.display().symbol();
            case "singular" -> currency.display().singular();
            case "plural" -> currency.display().plural();
            case "fractional_digits" -> Integer.toString(currency.display().fractionalDigits());
            default -> null;
        };
    }

    private boolean isCurrencyMetadata(String suffix) {
        return switch (suffix) {
            case "scope", "scope_type", "currency", "symbol", "singular", "plural", "fractional_digits" -> true;
            default -> false;
        };
    }

    private record PlaceholderRequest(EconomySettings.Currency currency, String suffix) {
        private static PlaceholderRequest parse(String rawParams, EconomySettings settings) {
            if (settings == null || rawParams == null || rawParams.length() > MAX_PARAMS_LENGTH) {
                return null;
            }
            String key = rawParams.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return null;
            }
            String currencyId;
            String suffix;
            if (key.startsWith(PRIMARY_PREFIX)) {
                currencyId = settings.vault().primaryCurrency();
                suffix = key.substring(PRIMARY_PREFIX.length());
            } else {
                suffix = matchingSuffix(key);
                if (suffix == null) {
                    return null;
                }
                currencyId = key.substring(0, key.length() - suffix.length() - 1);
            }
            EconomySettings.Currency currency = settings.currencies().get(currencyId);
            return currency == null || suffix.isBlank() ? null : new PlaceholderRequest(currency, suffix);
        }

        private static String matchingSuffix(String key) {
            for (String suffix : SUFFIXES) {
                String marker = "_" + suffix;
                if (key.endsWith(marker) && key.length() > marker.length()) {
                    return suffix;
                }
            }
            return null;
        }
    }
}
