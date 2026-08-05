package nl.hauntedmc.serverfeatures.features.notifylogin;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NotifyLoginDefaultsTest {

    @Test
    @SuppressWarnings("unchecked")
    void defaultConfigContainsOnlyTheSupremePlusPermissionOverride() {
        FeatureContext<Meta> context = mock(FeatureContext.class);
        NotifyLogin feature = new NotifyLogin(context);

        ConfigMap config = feature.getDefaultConfig();
        Map<String, Object> root = config.toMap();
        assertEquals(false, root.get("enabled"));
        assertEquals(true, root.get("announce_vanish_state_changes"));
        assertEquals(Map.of(
                "join", "notifylogin.default.join",
                "quit", "notifylogin.default.quit"
        ), root.get("default"));

        assertTrue(root.get("permission_overrides") instanceof Map<?, ?>);
        Map<String, Object> permissionOverrides = (Map<String, Object>) root.get("permission_overrides");
        assertEquals(Set.of("supremeplus"), permissionOverrides.keySet());
        assertEquals(Map.of(
                "priority", 100,
                "permission", "serverfeatures.feature.notifylogin.supremeplus",
                "join", "notifylogin.group.supremeplus.join",
                "quit", "notifylogin.group.supremeplus.quit"
        ), permissionOverrides.get("supremeplus"));
        assertEquals(Map.of(), root.get("player_overrides"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultMessagesMatchTheRequestedFormatting() {
        FeatureContext<Meta> context = mock(FeatureContext.class);
        NotifyLogin feature = new NotifyLogin(context);

        MessageMap messageMap = feature.getDefaultMessages();
        Map<String, String> messages = messageMap.getMessages();
        assertEquals(Set.of(
                "notifylogin.default.join",
                "notifylogin.default.quit",
                "notifylogin.group.supremeplus.join",
                "notifylogin.group.supremeplus.quit"
        ), messages.keySet());
        assertEquals(
                "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>] {name}",
                messages.get("notifylogin.default.join")
        );
        assertEquals(
                "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>] {name}",
                messages.get("notifylogin.default.quit")
        );
        assertEquals(
                "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>] "
                        + "<gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>"
                        + "[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%",
                messages.get("notifylogin.group.supremeplus.join")
        );
        assertEquals(
                "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>] "
                        + "<gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>"
                        + "[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%",
                messages.get("notifylogin.group.supremeplus.quit")
        );
    }

    @Test
    void featureVersionReflectsSyntheticVanishSelfDeliveryFix() {
        assertEquals("2.0.1", new Meta().getFeatureVersion());
    }
}
