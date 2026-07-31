package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.condition;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GsitConditionTest {

    @Test
    void hidesNametagWhenPlayerIsPassengerOfASeatMarker() {
        Player target = mock(Player.class);
        when(target.getVehicle()).thenReturn(mock(AreaEffectCloud.class));

        assertFalse(new GsitCondition().isVisible(mock(Player.class), target));
    }

    @Test
    void remainsCompatibleWithInverseLegacySeatMarkers() {
        Player target = mock(Player.class);
        when(target.getPassengers()).thenReturn(List.of(mock(AreaEffectCloud.class)));

        assertFalse(new GsitCondition().isVisible(mock(Player.class), target));
    }

    @Test
    void normalPlayersRemainVisible() {
        Player target = mock(Player.class);
        when(target.getPassengers()).thenReturn(List.of());

        assertTrue(new GsitCondition().isVisible(mock(Player.class), target));
    }
}
