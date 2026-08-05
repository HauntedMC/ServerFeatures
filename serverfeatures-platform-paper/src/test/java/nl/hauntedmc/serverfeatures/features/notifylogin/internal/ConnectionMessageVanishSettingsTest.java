package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionMessageVanishSettingsTest {

    @Test
    void vanishStateAnnouncementsDefaultToEnabled() {
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(Map.of(), "notifylogin"),
                ignored -> { }
        );

        assertTrue(settings.announceVanishStateChanges());
    }

    @Test
    void vanishStateAnnouncementsCanBeDisabled() {
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(Map.of("announce_vanish_state_changes", false), "notifylogin"),
                ignored -> { }
        );

        assertFalse(settings.announceVanishStateChanges());
    }

    @Test
    void malformedVanishStateSettingUsesSafeDefaultAndWarns() {
        List<String> warnings = new ArrayList<>();
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                ConfigNode.ofRaw(Map.of("announce_vanish_state_changes", "yes"), "notifylogin"),
                warnings::add
        );

        assertTrue(settings.announceVanishStateChanges());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("announce_vanish_state_changes")));
    }
}
