package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveInteractionPacketListenerTest {
    @Test
    void everyNormalEntityClickCanClaimAVirtualGrave() {
        assertTrue(GraveInteractionPacketListener.isClaimInteraction(
                WrapperPlayClientInteractEntity.InteractAction.INTERACT
        ));
        assertTrue(GraveInteractionPacketListener.isClaimInteraction(
                WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
        ));
        assertTrue(GraveInteractionPacketListener.isClaimInteraction(
                WrapperPlayClientInteractEntity.InteractAction.ATTACK
        ));
    }
}
