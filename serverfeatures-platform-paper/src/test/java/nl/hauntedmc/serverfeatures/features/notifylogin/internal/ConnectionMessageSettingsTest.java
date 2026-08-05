package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionMessageSettingsTest {

    @Test
    void resolvesPlayerPermissionAndDefaultLayersWithPerEventFallthrough() {
        UUID playerUuid = UUID.randomUUID();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default", Map.of(
                "join", "default.join",
                "quit", "default.quit"
        ));

        Map<String, Object> permissionOverrides = new LinkedHashMap<>();
        permissionOverrides.put("high", Map.of(
                "priority", 100,
                "permission", "notify.high",
                "quit", ""
        ));
        permissionOverrides.put("low", Map.of(
                "priority", 10,
                "permission", "notify.low",
                "join", "low.join",
                "quit", "low.quit"
        ));
        root.put("permission_overrides", permissionOverrides);

        Map<String, Object> playerOverrides = new LinkedHashMap<>();
        playerOverrides.put(playerUuid.toString(), Map.of("join", "uuid.join"));
        playerOverrides.put("ExamplePlayer", Map.of("quit", "name.quit"));
        root.put("player_overrides", playerOverrides);

        ConnectionMessageSettings settings = settings(root);
        Set<String> permissions = Set.of("notify.high", "notify.low");

        ConnectionMessageSettings.Resolution uuidJoin = settings.resolve(
                playerUuid,
                "ExamplePlayer",
                permissions::contains,
                ConnectionMessageSettings.EventType.JOIN
        );
        assertEquals("uuid.join", uuidJoin.messageKey());
        assertEquals("player:" + playerUuid, uuidJoin.source());

        ConnectionMessageSettings.Resolution uuidQuit = settings.resolve(
                playerUuid,
                "ExamplePlayer",
                permissions::contains,
                ConnectionMessageSettings.EventType.QUIT
        );
        assertEquals("name.quit", uuidQuit.messageKey());
        assertEquals("player:exampleplayer", uuidQuit.source());

        ConnectionMessageSettings.Resolution permissionJoin = settings.resolve(
                UUID.randomUUID(),
                "OtherPlayer",
                permissions::contains,
                ConnectionMessageSettings.EventType.JOIN
        );
        assertEquals("low.join", permissionJoin.messageKey());
        assertEquals("group:low", permissionJoin.source());

        ConnectionMessageSettings.Resolution permissionQuit = settings.resolve(
                UUID.randomUUID(),
                "OtherPlayer",
                permissions::contains,
                ConnectionMessageSettings.EventType.QUIT
        );
        assertTrue(permissionQuit.suppressed());
        assertEquals("group:high", permissionQuit.source());

        ConnectionMessageSettings.Resolution defaultJoin = settings.resolve(
                UUID.randomUUID(),
                "DefaultPlayer",
                ignored -> false,
                ConnectionMessageSettings.EventType.JOIN
        );
        assertEquals("default.join", defaultJoin.messageKey());
        assertEquals("default", defaultJoin.source());
    }

    @Test
    void explicitPlayerSuppressionDoesNotFallThrough() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default", Map.of("join", "default.join", "quit", "default.quit"));
        root.put("permission_overrides", Map.of(
                "vip", Map.of(
                        "priority", 10,
                        "permission", "notify.vip",
                        "join", "vip.join",
                        "quit", "vip.quit"
                )
        ));
        root.put("player_overrides", Map.of(
                "QuietPlayer", Map.of("join", "")
        ));

        ConnectionMessageSettings.Resolution resolution = settings(root).resolve(
                UUID.randomUUID(),
                "quietplayer",
                permission -> permission.equals("notify.vip"),
                ConnectionMessageSettings.EventType.JOIN
        );

        assertTrue(resolution.suppressed());
        assertEquals("player:quietplayer", resolution.source());
    }

    @Test
    void explicitNullAndMalformedValuesFailClosed() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default", Map.of("join", "default.join", "quit", "default.quit"));

        Map<String, Object> invalidProfile = new LinkedHashMap<>();
        invalidProfile.put("priority", "not-a-number");
        invalidProfile.put("permission", "notify.invalid");
        invalidProfile.put("join", Map.of("unexpected", "section"));
        invalidProfile.put("quit", null);
        root.put("permission_overrides", Map.of("invalid", invalidProfile));
        root.put("player_overrides", Map.of());

        List<String> warnings = new ArrayList<>();
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(root, "notifylogin"),
                warnings::add
        );

        ConnectionMessageSettings.Resolution join = settings.resolve(
                UUID.randomUUID(),
                "Player",
                permission -> permission.equals("notify.invalid"),
                ConnectionMessageSettings.EventType.JOIN
        );
        ConnectionMessageSettings.Resolution quit = settings.resolve(
                UUID.randomUUID(),
                "Player",
                permission -> permission.equals("notify.invalid"),
                ConnectionMessageSettings.EventType.QUIT
        );

        assertTrue(join.suppressed());
        assertTrue(quit.suppressed());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("localization key string")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("invalid priority")));
    }

    @Test
    void duplicateCaseInsensitivePermissionIdentifiersUseLastValue() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default", Map.of("join", "default.join", "quit", "default.quit"));

        Map<String, Object> permissionOverrides = new LinkedHashMap<>();
        permissionOverrides.put("VIP", Map.of(
                "priority", 10,
                "permission", "notify.old",
                "join", "old.join"
        ));
        permissionOverrides.put("vip", Map.of(
                "priority", 20,
                "permission", "notify.new",
                "join", "new.join"
        ));
        root.put("permission_overrides", permissionOverrides);
        root.put("player_overrides", Map.of());

        List<String> warnings = new ArrayList<>();
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(root, "notifylogin"),
                warnings::add
        );
        ConnectionMessageSettings.Resolution resolution = settings.resolve(
                UUID.randomUUID(),
                "Player",
                permission -> permission.equals("notify.new"),
                ConnectionMessageSettings.EventType.JOIN
        );

        assertEquals("new.join", resolution.messageKey());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("case-insensitive permission overrides")));
    }

    @Test
    void equalPrioritiesUseIdentifierOrderAndProduceAWarning() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default", Map.of("join", "default.join", "quit", "default.quit"));

        Map<String, Object> permissionOverrides = new LinkedHashMap<>();
        permissionOverrides.put("zeta", Map.of(
                "priority", 50,
                "permission", "notify.zeta",
                "join", "zeta.join"
        ));
        permissionOverrides.put("alpha", Map.of(
                "priority", 50,
                "permission", "notify.alpha",
                "join", "alpha.join"
        ));
        root.put("permission_overrides", permissionOverrides);
        root.put("player_overrides", Map.of());

        List<String> warnings = new ArrayList<>();
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(root, "notifylogin"),
                warnings::add
        );
        ConnectionMessageSettings.Resolution resolution = settings.resolve(
                UUID.randomUUID(),
                "Player",
                ignored -> true,
                ConnectionMessageSettings.EventType.JOIN
        );

        assertEquals("alpha.join", resolution.messageKey());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.getFirst().contains("same priority"));
    }

    private static ConnectionMessageSettings settings(Map<String, Object> root) {
        return ConnectionMessageSettings.from(
                ConfigNode.ofRaw(root, "notifylogin"),
                ignored -> { }
        );
    }
}
