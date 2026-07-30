package nl.hauntedmc.serverfeatures.features.playercount.internal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * PlaceholderAPI expansion for network, local-server and named-server counts.
 */
public final class PlayerCountPlaceholder extends PlaceholderExpansion {

    private static final String SERVER_PREFIX = "server_";

    private final PlayerCountAPI api;

    public PlayerCountPlaceholder(PlayerCountAPI api) {
        this.api = java.util.Objects.requireNonNull(api, "api");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playercount";
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
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "available" -> Boolean.toString(api.isAvailable());
            case "stale" -> Boolean.toString(api.isStale());
            case "age_seconds" -> ageSeconds();
            case "published_at" -> Long.toString(api.publishedAtEpochMillis());
            case "network_online" -> metric(api.network(), Metric.ONLINE);
            case "network_visible" -> metric(api.network(), Metric.VISIBLE);
            case "network_vanished" -> metric(api.network(), Metric.VANISHED);
            case "server_online" -> metric(api.localServer(), Metric.ONLINE);
            case "server_visible" -> metric(api.localServer(), Metric.VISIBLE);
            case "server_vanished" -> metric(api.localServer(), Metric.VANISHED);
            default -> namedServerMetric(normalized);
        };
    }

    private String namedServerMetric(String params) {
        if (!params.startsWith(SERVER_PREFIX)) {
            return null;
        }
        String remainder = params.substring(SERVER_PREFIX.length());
        int separator = remainder.lastIndexOf('_');
        if (separator <= 0 || separator == remainder.length() - 1) {
            return null;
        }
        String serverName = remainder.substring(0, separator);
        Metric metric = Metric.from(remainder.substring(separator + 1));
        if (metric == null) {
            return null;
        }
        return metric(api.server(serverName), metric);
    }

    private String ageSeconds() {
        long ageMillis = api.ageMillis();
        return ageMillis < 0L ? "-1" : Long.toString(ageMillis / 1_000L);
    }

    private static String metric(Optional<PlayerCountSnapshot.Counts> counts, Metric metric) {
        if (counts.isEmpty()) {
            return "0";
        }
        PlayerCountSnapshot.Counts value = counts.get();
        return Integer.toString(switch (metric) {
            case ONLINE -> value.online();
            case VISIBLE -> value.visible();
            case VANISHED -> value.vanished();
        });
    }

    private enum Metric {
        ONLINE,
        VISIBLE,
        VANISHED;

        private static Metric from(String value) {
            return switch (value) {
                case "online" -> ONLINE;
                case "visible" -> VISIBLE;
                case "vanished" -> VANISHED;
                default -> null;
            };
        }
    }
}
