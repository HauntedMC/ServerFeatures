package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Immutable, validated NotifyLogin message selection configuration. */
public final class ConnectionMessageSettings {
    private static final boolean DEFAULT_ANNOUNCE_VANISH_STATE_CHANGES = true;

    private final boolean announceVanishStateChanges;
    private final MessageOverride defaultMessages;
    private final List<PermissionOverride> permissionOverrides;
    private final Map<UUID, MessageOverride> playerOverridesByUuid;
    private final Map<String, MessageOverride> playerOverridesByName;

    private ConnectionMessageSettings(
            boolean announceVanishStateChanges,
            MessageOverride defaultMessages,
            List<PermissionOverride> permissionOverrides,
            Map<UUID, MessageOverride> playerOverridesByUuid,
            Map<String, MessageOverride> playerOverridesByName
    ) {
        this.announceVanishStateChanges = announceVanishStateChanges;
        this.defaultMessages = Objects.requireNonNull(defaultMessages, "defaultMessages");
        this.permissionOverrides = List.copyOf(permissionOverrides);
        this.playerOverridesByUuid = Map.copyOf(playerOverridesByUuid);
        this.playerOverridesByName = Map.copyOf(playerOverridesByName);
    }

    public static ConnectionMessageSettings from(ConfigNode root, Consumer<String> warningSink) {
        Objects.requireNonNull(root, "root");
        Consumer<String> warnings = warningSink == null ? ignored -> { } : warningSink;
        boolean announceVanishStateChanges = parseBoolean(
                root.get("announce_vanish_state_changes"),
                DEFAULT_ANNOUNCE_VANISH_STATE_CHANGES,
                warnings,
                "announce_vanish_state_changes"
        );
        MessageOverride defaults = parseMessages(root.get("default"), true, warnings, "default");
        List<PermissionOverride> permissions = parsePermissionOverrides(root.get("permission_overrides"), warnings);
        Map<UUID, MessageOverride> uuidOverrides = new LinkedHashMap<>();
        Map<String, MessageOverride> nameOverrides = new LinkedHashMap<>();
        parsePlayerOverrides(root.get("player_overrides"), uuidOverrides, nameOverrides, warnings);
        return new ConnectionMessageSettings(
                announceVanishStateChanges,
                defaults,
                permissions,
                uuidOverrides,
                nameOverrides
        );
    }

    public boolean announceVanishStateChanges() {
        return announceVanishStateChanges;
    }

    public Resolution resolve(
            UUID playerUuid,
            String playerName,
            Predicate<String> permissionChecker,
            EventType eventType
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(permissionChecker, "permissionChecker");
        Objects.requireNonNull(eventType, "eventType");

        Resolution resolution = resolve(playerOverridesByUuid.get(playerUuid), eventType, "player:" + playerUuid);
        if (resolution != null) return resolution;

        String normalizedName = normalizeName(playerName);
        if (!normalizedName.isEmpty()) {
            resolution = resolve(playerOverridesByName.get(normalizedName), eventType, "player:" + normalizedName);
            if (resolution != null) return resolution;
        }

        for (PermissionOverride override : permissionOverrides) {
            if (!permissionChecker.test(override.permission())) continue;
            resolution = resolve(override.messages(), eventType, "group:" + override.id());
            if (resolution != null) return resolution;
        }

        Resolution defaultResolution = resolve(defaultMessages, eventType, "default");
        return defaultResolution == null ? Resolution.suppressed("default") : defaultResolution;
    }

    private static List<PermissionOverride> parsePermissionOverrides(ConfigNode node, Consumer<String> warnings) {
        if (node.isNull()) {
            return List.of();
        }
        if (!(node.raw() instanceof Map<?, ?>)) {
            warnings.accept("NotifyLogin setting 'permission_overrides' must be a section; all permission overrides were ignored.");
            return List.of();
        }

        Map<String, PermissionOverride> overridesById = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : node.children().entrySet()) {
            String configuredId = entry.getKey() == null ? "" : entry.getKey().trim();
            String id = normalizeIdentifier(configuredId);
            ConfigNode profile = entry.getValue();
            if (id.isEmpty()) {
                warnings.accept("NotifyLogin permission override has an empty identifier and was ignored.");
                continue;
            }
            if (!(profile.raw() instanceof Map<?, ?>)) {
                warnings.accept("NotifyLogin permission override '" + configuredId
                        + "' must be a section and was ignored.");
                continue;
            }

            Object permissionRaw = profile.get("permission").raw();
            if (!(permissionRaw instanceof String permissionValue) || permissionValue.trim().isEmpty()) {
                warnings.accept("NotifyLogin permission override '" + configuredId
                        + "' has no valid permission and was ignored.");
                continue;
            }
            String permission = permissionValue.trim();
            MessageOverride messages = parseMessages(
                    profile,
                    false,
                    warnings,
                    "permission_overrides." + configuredId
            );
            if (!messages.hasDefinedValue()) {
                warnings.accept("NotifyLogin permission override '" + configuredId
                        + "' defines neither join nor quit and was ignored.");
                continue;
            }

            int priority = parsePriority(profile.get("priority"), warnings, configuredId);
            PermissionOverride replacement = new PermissionOverride(id, priority, permission, messages);
            if (overridesById.put(id, replacement) != null) {
                warnings.accept("NotifyLogin contains multiple case-insensitive permission overrides named '"
                        + configuredId + "'; the last value is used.");
            }
        }

        List<PermissionOverride> overrides = new ArrayList<>(overridesById.values());
        overrides.sort(Comparator.comparingInt(PermissionOverride::priority).reversed()
                .thenComparing(PermissionOverride::id, String.CASE_INSENSITIVE_ORDER));
        for (int index = 1; index < overrides.size(); index++) {
            PermissionOverride previous = overrides.get(index - 1);
            PermissionOverride current = overrides.get(index);
            if (previous.priority() == current.priority()) {
                warnings.accept("NotifyLogin permission overrides '" + previous.id() + "' and '"
                        + current.id() + "' use the same priority " + current.priority()
                        + "; identifier order is used as deterministic tie-breaker.");
            }
        }
        return overrides;
    }

    private static void parsePlayerOverrides(
            ConfigNode node,
            Map<UUID, MessageOverride> uuidOverrides,
            Map<String, MessageOverride> nameOverrides,
            Consumer<String> warnings
    ) {
        if (node.isNull()) {
            return;
        }
        if (!(node.raw() instanceof Map<?, ?>)) {
            warnings.accept("NotifyLogin setting 'player_overrides' must be a section; all player overrides were ignored.");
            return;
        }

        for (Map.Entry<String, ConfigNode> entry : node.children().entrySet()) {
            String identity = entry.getKey() == null ? "" : entry.getKey().trim();
            if (identity.isEmpty()) {
                warnings.accept("NotifyLogin player override has an empty identity and was ignored.");
                continue;
            }
            MessageOverride messages = parseMessages(
                    entry.getValue(),
                    false,
                    warnings,
                    "player_overrides." + identity
            );
            if (!messages.hasDefinedValue()) {
                warnings.accept("NotifyLogin player override '" + identity
                        + "' defines neither join nor quit and was ignored.");
                continue;
            }
            try {
                UUID uuid = UUID.fromString(identity);
                if (uuidOverrides.put(uuid, messages) != null) {
                    warnings.accept("NotifyLogin contains multiple overrides for UUID " + uuid
                            + "; the last value is used.");
                }
            } catch (IllegalArgumentException ignored) {
                String normalizedName = normalizeName(identity);
                if (nameOverrides.put(normalizedName, messages) != null) {
                    warnings.accept("NotifyLogin contains multiple case-insensitive overrides for player '"
                            + identity + "'; the last value is used.");
                }
            }
        }
    }

    private static MessageOverride parseMessages(
            ConfigNode node,
            boolean defaultLayer,
            Consumer<String> warnings,
            String context
    ) {
        if (node.isNull()) {
            return fallbackMessages(defaultLayer);
        }
        if (!(node.raw() instanceof Map<?, ?>)) {
            warnings.accept("NotifyLogin setting '" + context
                    + "' must be a section; its messages were ignored.");
            return fallbackMessages(defaultLayer);
        }

        Map<String, ConfigNode> fields = node.children();
        return new MessageOverride(
                parseMessageValue(fields, "join", defaultLayer, warnings, context),
                parseMessageValue(fields, "quit", defaultLayer, warnings, context)
        );
    }

    private static MessageOverride fallbackMessages(boolean defaultLayer) {
        MessageValue fallback = defaultLayer ? MessageValue.suppress() : MessageValue.inherit();
        return new MessageOverride(fallback, fallback);
    }

    private static MessageValue parseMessageValue(
            Map<String, ConfigNode> fields,
            String key,
            boolean defaultLayer,
            Consumer<String> warnings,
            String context
    ) {
        if (!fields.containsKey(key)) {
            return defaultLayer ? MessageValue.suppress() : MessageValue.inherit();
        }

        ConfigNode node = fields.get(key);
        if (node == null || node.isNull()) {
            return MessageValue.suppress();
        }
        if (!(node.raw() instanceof String configuredValue)) {
            warnings.accept("NotifyLogin setting '" + context + "." + key
                    + "' must be a localization key string or empty; this message was suppressed.");
            return MessageValue.suppress();
        }

        String configured = configuredValue.trim();
        return configured.isEmpty() ? MessageValue.suppress() : MessageValue.message(configured);
    }

    private static boolean parseBoolean(
            ConfigNode node,
            boolean fallback,
            Consumer<String> warnings,
            String key
    ) {
        Object raw = node.raw();
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean configured) {
            return configured;
        }
        warnings.accept("NotifyLogin setting '" + key + "' must be true or false; using " + fallback + ".");
        return fallback;
    }

    private static int parsePriority(ConfigNode node, Consumer<String> warnings, String id) {
        Object raw = node.raw();
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value)
                    && value == Math.rint(value)
                    && value >= Integer.MIN_VALUE
                    && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        } else if (raw instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                // Warn below and use the deterministic fallback.
            }
        }

        warnings.accept("NotifyLogin permission override '" + id
                + "' has invalid priority '" + raw + "'; using 0.");
        return 0;
    }

    private static Resolution resolve(MessageOverride override, EventType eventType, String source) {
        if (override == null) return null;
        MessageValue value = override.forEvent(eventType);
        if (!value.defined()) return null;
        return value.messageKey() == null
                ? Resolution.suppressed(source)
                : new Resolution(value.messageKey(), source);
    }

    private static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum EventType { JOIN, QUIT }

    public record Resolution(String messageKey, String source) {
        public Resolution {
            Objects.requireNonNull(source, "source");
        }

        public static Resolution suppressed(String source) {
            return new Resolution(null, source);
        }

        public boolean suppressed() {
            return messageKey == null;
        }
    }

    private record PermissionOverride(String id, int priority, String permission, MessageOverride messages) { }

    private record MessageOverride(MessageValue join, MessageValue quit) {
        private MessageValue forEvent(EventType eventType) {
            return eventType == EventType.JOIN ? join : quit;
        }

        private boolean hasDefinedValue() {
            return join.defined() || quit.defined();
        }
    }

    private record MessageValue(boolean defined, String messageKey) {
        private static MessageValue inherit() { return new MessageValue(false, null); }
        private static MessageValue suppress() { return new MessageValue(true, null); }
        private static MessageValue message(String key) {
            return new MessageValue(true, Objects.requireNonNull(key, "key"));
        }
    }
}
