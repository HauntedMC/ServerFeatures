package nl.hauntedmc.serverfeatures.features.economy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyBalance;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/** Cache-only PlaceholderAPI expansion. */
public final class EconomyPlaceholder extends PlaceholderExpansion {
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
        if (feature.service() == null || player == null) {
            return "0";
        }
        String key = params.trim().toLowerCase(Locale.ROOT);
        String currencyId;
        String suffix;
        if (key.startsWith("primary_")) {
            currencyId = feature.settings().vault().primaryCurrency();
            suffix = key.substring("primary_".length());
        } else {
            int separator = key.lastIndexOf('_');
            if (separator <= 0) {
                return null;
            }
            currencyId = key.substring(0, separator);
            suffix = key.substring(separator + 1);
        }
        EconomySettings.Currency currency = feature.settings().currencies().get(currencyId);
        if (currency == null) {
            return null;
        }
        EconomyAccountRef account = new EconomyAccountRef(
                null,
                player.getUniqueId(),
                player.getName(),
                currency.id(),
                currency.scope().key()
        );
        Optional<EconomyBalance> balance = feature.service().cachedBalance(account);
        return switch (suffix) {
            case "balance" -> balance.map(value -> feature.service().format(currency.id(), value.balance())).orElse("0");
            case "raw" -> balance.map(value -> value.balance().toPlainString()).orElse("0");
            case "scope" -> currency.scope().key();
            case "payments" -> feature.service().cachedAccount(player.getUniqueId(), currency.id())
                    .map(value -> Boolean.toString(value.paymentsEnabled())).orElse("true");
            default -> null;
        };
    }
}
