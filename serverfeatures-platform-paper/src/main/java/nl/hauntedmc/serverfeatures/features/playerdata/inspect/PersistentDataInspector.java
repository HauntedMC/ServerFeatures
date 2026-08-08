package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import nl.hauntedmc.serverfeatures.features.playerdata.model.PlayerDataEntry;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class PersistentDataInspector {

    public List<PlayerDataEntry> inspect(
            PersistentDataContainer container,
            Predicate<NamespacedKey> filter,
            int maxEntries,
            int maxValueLength
    ) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(filter, "filter");
        if (maxEntries < 1 || maxValueLength < 1) {
            throw new IllegalArgumentException("Inspection limits must be positive");
        }

        List<NamespacedKey> keys = container.getKeys().stream()
                .filter(filter)
                .sorted(Comparator.comparing(NamespacedKey::toString))
                .limit(maxEntries)
                .toList();
        List<PlayerDataEntry> entries = new ArrayList<>(keys.size());
        for (NamespacedKey key : keys) {
            entries.add(read(container, key, maxValueLength));
        }
        return List.copyOf(entries);
    }

    public long count(PersistentDataContainer container, Predicate<NamespacedKey> filter) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(filter, "filter");
        return container.getKeys().stream().filter(filter).count();
    }

    public static boolean isServerFeaturesKey(NamespacedKey key) {
        return key != null && "serverfeatures".equals(key.getNamespace());
    }

    private PlayerDataEntry read(
            PersistentDataContainer container,
            NamespacedKey key,
            int maxValueLength
    ) {
        if (container.has(key, PersistentDataType.BYTE)) {
            Byte value = container.get(key, PersistentDataType.BYTE);
            String rendered = value != null && (value == 0 || value == 1)
                    ? value + (value == 1 ? " (true)" : " (false)")
                    : String.valueOf(value);
            return entry(key, "byte", rendered, maxValueLength);
        }
        if (container.has(key, PersistentDataType.SHORT)) {
            return entry(key, "short", container.get(key, PersistentDataType.SHORT), maxValueLength);
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return entry(key, "int", container.get(key, PersistentDataType.INTEGER), maxValueLength);
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return entry(key, "long", container.get(key, PersistentDataType.LONG), maxValueLength);
        }
        if (container.has(key, PersistentDataType.FLOAT)) {
            return entry(key, "float", container.get(key, PersistentDataType.FLOAT), maxValueLength);
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return entry(key, "double", container.get(key, PersistentDataType.DOUBLE), maxValueLength);
        }
        if (container.has(key, PersistentDataType.STRING)) {
            return entry(key, "string", container.get(key, PersistentDataType.STRING), maxValueLength);
        }
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
            return entry(
                    key,
                    "byte[]",
                    Arrays.toString(container.get(key, PersistentDataType.BYTE_ARRAY)),
                    maxValueLength
            );
        }
        if (container.has(key, PersistentDataType.INTEGER_ARRAY)) {
            return entry(
                    key,
                    "int[]",
                    Arrays.toString(container.get(key, PersistentDataType.INTEGER_ARRAY)),
                    maxValueLength
            );
        }
        if (container.has(key, PersistentDataType.LONG_ARRAY)) {
            return entry(
                    key,
                    "long[]",
                    Arrays.toString(container.get(key, PersistentDataType.LONG_ARRAY)),
                    maxValueLength
            );
        }
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            PersistentDataContainer nested = container.get(key, PersistentDataType.TAG_CONTAINER);
            int keyCount = nested == null ? 0 : nested.getKeys().size();
            return entry(key, "container", keyCount + " keys", maxValueLength);
        }
        if (container.has(key, PersistentDataType.TAG_CONTAINER_ARRAY)) {
            PersistentDataContainer[] nested = container.get(key, PersistentDataType.TAG_CONTAINER_ARRAY);
            return entry(
                    key,
                    "container[]",
                    (nested == null ? 0 : nested.length) + " entries",
                    maxValueLength
            );
        }
        return new PlayerDataEntry(key.toString(), "custom/unknown", "<value not decoded>");
    }

    private static PlayerDataEntry entry(
            NamespacedKey key,
            String type,
            Object value,
            int maxValueLength
    ) {
        return new PlayerDataEntry(key.toString(), type, truncate(String.valueOf(value), maxValueLength));
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
