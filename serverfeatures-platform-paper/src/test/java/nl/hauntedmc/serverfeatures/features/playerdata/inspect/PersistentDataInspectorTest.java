package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import nl.hauntedmc.serverfeatures.features.playerdata.model.PlayerDataEntry;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistentDataInspectorTest {

    @Test
    void filtersAndRendersServerFeaturesSettings() {
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        NamespacedKey autoPickup = new NamespacedKey("serverfeatures", "autopickup_enabled");
        NamespacedKey fly = new NamespacedKey("serverfeatures", "fly_enabled");
        NamespacedKey foreign = new NamespacedKey("otherplugin", "flag");
        when(container.getKeys()).thenReturn(Set.of(foreign, fly, autoPickup));
        when(container.has(autoPickup, PersistentDataType.BYTE)).thenReturn(true);
        when(container.has(fly, PersistentDataType.BYTE)).thenReturn(true);
        when(container.get(autoPickup, PersistentDataType.BYTE)).thenReturn((byte) 0);
        when(container.get(fly, PersistentDataType.BYTE)).thenReturn((byte) 1);

        PersistentDataInspector inspector = new PersistentDataInspector();
        List<PlayerDataEntry> entries = inspector.inspect(
                container,
                PersistentDataInspector::isServerFeaturesKey,
                100,
                240
        );

        assertEquals(2, entries.size());
        assertEquals("serverfeatures:autopickup_enabled", entries.get(0).key());
        assertEquals("0 (false)", entries.get(0).value());
        assertEquals("serverfeatures:fly_enabled", entries.get(1).key());
        assertEquals("1 (true)", entries.get(1).value());
    }

    @Test
    void appliesEntryAndValueLimits() {
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        NamespacedKey first = new NamespacedKey("serverfeatures", "a");
        NamespacedKey second = new NamespacedKey("serverfeatures", "b");
        when(container.getKeys()).thenReturn(Set.of(first, second));
        when(container.has(first, PersistentDataType.STRING)).thenReturn(true);
        when(container.get(first, PersistentDataType.STRING)).thenReturn("abcdefghijklmnopqrstuvwxyz");

        List<PlayerDataEntry> entries = new PersistentDataInspector().inspect(
                container,
                ignored -> true,
                1,
                10
        );

        assertEquals(1, entries.size());
        assertEquals("abcdefghi…", entries.getFirst().value());
    }
}
