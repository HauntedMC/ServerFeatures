package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parses and validates primitive Economy configuration values. */
final class EconomyConfigValues {
    private static final String KEY_PATTERN = "[a-z0-9][a-z0-9_.-]{0,63}";

    private EconomyConfigValues() {
    }

    static String currencyId(String value) {
        String id = requireText(value, "currency id").toLowerCase(Locale.ROOT);
        if (!id.matches(KEY_PATTERN)) throw new IllegalArgumentException("Invalid currency id: " + value);
        return id;
    }

    static EconomyScopeType scopeType(String raw, String field) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("LOCAL") || normalized.equals("GAMEMODE")) return EconomyScopeType.SERVER;
        return enumValue(EconomyScopeType.class, normalized, field);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    static String localScopeKey(ConfigNode node, String currencyId, String fallback) {
        String configured = text(node, "scope.local_key", "");
        if (configured.isBlank()) configured = text(node, "scope.gamemode_key", "");
        if (configured.isBlank()) configured = text(node, "scope.server_key", "");
        return key(configured.isBlank() ? fallback : configured, "currencies." + currencyId + ".scope.local_key");
    }

    static void validateCommandLabels(Iterable<EconomySettings.Currency> currencies) {
        Set<String> labels = new LinkedHashSet<>();
        for (EconomySettings.Currency currency : currencies) {
            List<String> candidates = new ArrayList<>();
            candidates.add(currency.commands().root());
            candidates.addAll(currency.commands().aliases());
            for (String label : candidates) {
                if (!labels.add(label.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate economy command label: " + label);
                }
            }
        }
    }

    static List<String> aliases(ConfigNode node) {
        if (node.isNull()) return List.of();
        List<String> result = new ArrayList<>();
        for (String alias : node.listOf(String.class)) {
            String normalized = commandLabel(alias);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    static String commandLabel(String value) {
        String label = requireText(value, "command label").toLowerCase(Locale.ROOT);
        if (!label.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Invalid command label: " + value);
        }
        return label;
    }

    static String key(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches(KEY_PATTERN)) throw new IllegalArgumentException(field + " must match " + KEY_PATTERN);
        return normalized;
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    static String text(ConfigNode node, String path, String fallback) {
        String value = node.getAt(path).as(String.class, fallback);
        return value == null ? fallback : value.trim();
    }

    static boolean bool(ConfigNode node, String path, boolean fallback) {
        return node.getAt(path).as(Boolean.class, fallback);
    }

    static int integer(ConfigNode node, String path, int fallback, int minimum, int maximum) {
        int value = node.getAt(path).as(Integer.class, fallback);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    static BigDecimal amount(ConfigNode node, String path, String fallback, int digits, RoundingMode rounding) {
        String raw = text(node, path, fallback);
        try {
            BigDecimal value = new BigDecimal(raw);
            long integerDigits = (long) value.precision() - value.scale();
            if (value.scale() > 8 || value.signum() != 0 && integerDigits > 30L) {
                throw new IllegalArgumentException(path + " exceeds DECIMAL(38,8) storage precision");
            }
            if (value.scale() > digits) value = value.setScale(digits, rounding);
            BigDecimal normalized = value.setScale(digits, rounding);
            if (normalized.precision() > 38) {
                throw new IllegalArgumentException(path + " exceeds DECIMAL(38,8) storage precision");
            }
            return normalized;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid amount at " + path + ": " + raw, exception);
        }
    }

    static BigDecimal positiveAmount(ConfigNode node, String path, int digits, RoundingMode rounding, String fallback) {
        BigDecimal value = amount(node, path, fallback, digits, rounding);
        if (value.signum() <= 0) throw new IllegalArgumentException(path + " must be positive");
        return value;
    }

    static BigDecimal nonNegativeAmount(ConfigNode node, String path, int digits, RoundingMode rounding, String fallback) {
        BigDecimal value = amount(node, path, fallback, digits, rounding);
        if (value.signum() < 0) throw new IllegalArgumentException(path + " must not be negative");
        return value;
    }

    static Duration duration(ConfigNode node, String path, String fallback, Duration minimum, Duration maximum) {
        String raw = text(node, path, fallback).toLowerCase(Locale.ROOT);
        long multiplier;
        String number;
        if (raw.endsWith("ms")) { multiplier = 1L; number = raw.substring(0, raw.length() - 2); }
        else if (raw.endsWith("s")) { multiplier = 1_000L; number = raw.substring(0, raw.length() - 1); }
        else if (raw.endsWith("m")) { multiplier = 60_000L; number = raw.substring(0, raw.length() - 1); }
        else if (raw.endsWith("h")) { multiplier = 3_600_000L; number = raw.substring(0, raw.length() - 1); }
        else throw new IllegalArgumentException("Invalid duration at " + path + ": " + raw);
        try {
            Duration value = Duration.ofMillis(Math.multiplyExact(Long.parseLong(number.trim()), multiplier));
            if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
                throw new IllegalArgumentException(path + " is outside the allowed range");
            }
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid duration at " + path + ": " + raw, exception);
        }
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw, exception);
        }
    }
}
