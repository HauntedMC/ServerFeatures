package nl.hauntedmc.serverfeatures.features.capacity.internal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/** PlaceholderAPI expansion for authoritative network, gameplay, group and server capacity. */
public final class CapacityPlaceholder extends PlaceholderExpansion {

    private static final String NETWORK_PREFIX = "network_";
    private static final String GAMEPLAY_PREFIX = "gameplay_";
    private static final String GROUP_PREFIX = "group_";
    private static final String SERVER_PREFIX = "server_";

    private final CapacityAPI api;

    public CapacityPlaceholder(CapacityAPI api) {
        this.api = java.util.Objects.requireNonNull(api, "api");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "capacity";
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
            case "active_leases" -> Integer.toString(api.activeLeases());
            default -> scopeValue(normalized);
        };
    }

    private String scopeValue(String params) {
        if (params.startsWith(NETWORK_PREFIX)) {
            Metric metric = Metric.fromExact(params.substring(NETWORK_PREFIX.length()));
            return metric == null ? null : metric.render(api.network());
        }
        if (params.startsWith(GAMEPLAY_PREFIX)) {
            Metric metric = Metric.fromExact(params.substring(GAMEPLAY_PREFIX.length()));
            return metric == null ? null : metric.render(api.gameplay());
        }
        if (params.startsWith(GROUP_PREFIX)) {
            ParsedScope parsed = ParsedScope.parse(params.substring(GROUP_PREFIX.length()));
            return parsed == null ? null : parsed.metric().render(api.group(parsed.name()));
        }
        if (params.startsWith(SERVER_PREFIX)) {
            String remainder = params.substring(SERVER_PREFIX.length());
            Metric localMetric = Metric.fromExact(remainder);
            if (localMetric != null) {
                return localMetric.render(api.localServer());
            }
            ParsedScope parsed = ParsedScope.parse(remainder);
            return parsed == null ? null : parsed.metric().render(api.server(parsed.name()));
        }
        return null;
    }

    private String ageSeconds() {
        long ageMillis = api.ageMillis();
        return ageMillis < 0L ? "-1" : Long.toString(ageMillis / 1_000L);
    }

    private enum Metric {
        RESTORATION_RESERVED("restoration_reserved"),
        NORMAL_AVAILABLE("normal_available"),
        ABSOLUTE_AVAILABLE("absolute_available"),
        RESERVED_AVAILABLE("reserved_available"),
        NORMAL_CAPACITY("normal_capacity"),
        ABSOLUTE_FULL("absolute_full"),
        RESERVED("reserved"),
        OCCUPIED("occupied"),
        PENDING("pending"),
        CAPACITY("capacity"),
        AVAILABLE("available"),
        ACCEPTING("accepting"),
        LIMITED("limited"),
        EXISTS("exists"),
        STATE("state"),
        OPEN("open"),
        FULL("full"),
        USED("used");

        private static final Metric[] PARSE_ORDER = Arrays.stream(values())
                .sorted(Comparator.comparingInt((Metric metric) -> metric.suffix.length()).reversed())
                .toArray(Metric[]::new);

        private final String suffix;

        Metric(String suffix) {
            this.suffix = suffix;
        }

        private static Metric fromExact(String value) {
            for (Metric metric : values()) {
                if (metric.suffix.equals(value)) {
                    return metric;
                }
            }
            return null;
        }

        private String render(Optional<CapacitySnapshot.Scope> scope) {
            if (this == EXISTS) {
                return Boolean.toString(scope.isPresent());
            }
            if (scope.isEmpty()) {
                return switch (this) {
                    case STATE -> "unavailable";
                    case OPEN, FULL, ABSOLUTE_FULL, ACCEPTING, LIMITED -> "false";
                    default -> "0";
                };
            }

            CapacitySnapshot.Scope value = scope.get();
            return switch (this) {
                case CAPACITY -> Integer.toString(value.capacity());
                case RESERVED -> Integer.toString(value.reservedSlots());
                case NORMAL_CAPACITY -> Integer.toString(value.normalCapacity());
                case OCCUPIED -> Integer.toString(value.occupied());
                case PENDING -> Integer.toString(value.pending());
                case RESTORATION_RESERVED -> Integer.toString(value.restorationReserved());
                case USED -> Integer.toString(value.effectiveUsed());
                case AVAILABLE, NORMAL_AVAILABLE -> Integer.toString(value.normalAvailable());
                case ABSOLUTE_AVAILABLE -> Integer.toString(value.absoluteAvailable());
                case RESERVED_AVAILABLE -> Integer.toString(
                        Math.max(0, value.absoluteAvailable() - value.normalAvailable())
                );
                case STATE -> value.state().name().toLowerCase(Locale.ROOT);
                case OPEN -> Boolean.toString(value.isOpen());
                case FULL -> Boolean.toString(value.isLimited() && value.normalAvailable() <= 0);
                case ABSOLUTE_FULL -> Boolean.toString(
                        value.isLimited() && value.absoluteAvailable() <= 0
                );
                case ACCEPTING -> Boolean.toString(value.isAcceptingNormalPlayers());
                case LIMITED -> Boolean.toString(value.isLimited());
                case EXISTS -> "true";
            };
        }
    }

    private record ParsedScope(String name, Metric metric) {
        private static ParsedScope parse(String value) {
            for (Metric metric : Metric.PARSE_ORDER) {
                String suffix = "_" + metric.suffix;
                if (!value.endsWith(suffix) || value.length() <= suffix.length()) {
                    continue;
                }
                String name = value.substring(0, value.length() - suffix.length());
                if (!name.isBlank()) {
                    return new ParsedScope(name, metric);
                }
            }
            return null;
        }
    }
}
