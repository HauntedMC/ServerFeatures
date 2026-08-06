package nl.hauntedmc.serverfeatures.api.economy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class EconomyRequestValidation {
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_METADATA_KEY_LENGTH = 64;
    private static final int MAX_METADATA_VALUE_LENGTH = 512;
    private static final int MAX_METADATA_TOTAL_LENGTH = 2_048;

    private EconomyRequestValidation() {
    }

    static String source(String value) {
        String normalized = text(value, "source", 64, true).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.:-]{0,63}")) {
            throw new IllegalArgumentException("source contains unsupported characters");
        }
        return normalized;
    }

    static String text(String value, String name, int maximumLength, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    static Map<String, String> metadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw new IllegalArgumentException("metadata must not exceed " + MAX_METADATA_ENTRIES + " entries");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        int totalLength = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = text(entry.getKey(), "metadata key", MAX_METADATA_KEY_LENGTH, true);
            String value = text(entry.getValue(), "metadata value", MAX_METADATA_VALUE_LENGTH, false);
            totalLength = Math.addExact(totalLength, Math.addExact(key.length(), value.length()));
            if (totalLength > MAX_METADATA_TOTAL_LENGTH) {
                throw new IllegalArgumentException(
                        "metadata must not exceed " + MAX_METADATA_TOTAL_LENGTH + " total characters"
                );
            }
            if (copy.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("metadata contains a duplicate normalized key: " + key);
            }
        }
        return Map.copyOf(copy);
    }
}
