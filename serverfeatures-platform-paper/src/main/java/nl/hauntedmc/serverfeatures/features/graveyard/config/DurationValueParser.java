package nl.hauntedmc.serverfeatures.features.graveyard.config;

import java.util.Locale;

final class DurationValueParser {
    private DurationValueParser() {
    }

    static long parseMillis(Object value, long fallback) {
        if (value instanceof Number number) {
            return positiveOrFallback(number.longValue(), fallback);
        }
        if (!(value instanceof String text)) {
            return fallback;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return fallback;
        }
        try {
            if (normalized.endsWith("ms")) {
                return positiveOrFallback(Long.parseLong(normalized.substring(0, normalized.length() - 2)), fallback);
            }
            char suffix = normalized.charAt(normalized.length() - 1);
            long multiplier = switch (suffix) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> 1L;
            };
            String number = Character.isLetter(suffix)
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
            return positiveOrFallback(Math.multiplyExact(Long.parseLong(number), multiplier), fallback);
        } catch (ArithmeticException | NumberFormatException exception) {
            return fallback;
        }
    }

    private static long positiveOrFallback(long value, long fallback) {
        return value > 0L ? value : fallback;
    }
}
