package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;

import java.util.UUID;

/**
 * Keeps fake nametag entities synchronized with vanilla entity lifecycle packets.
 *
 * <p>Passenger replacements retain the display passenger, while owner-destroy packets are expanded
 * with the related fake entity id. This prevents silent detaches and client-side ghost displays even
 * when the packet originates in vanilla tracking code or another plugin.</p>
 */
public final class NametagPassengerPacketListener extends PacketListenerAbstract {
    private final NametagAttachmentIndex attachmentIndex;

    public NametagPassengerPacketListener(NametagAttachmentIndex attachmentIndex) {
        super(PacketListenerPriority.HIGHEST);
        this.attachmentIndex = attachmentIndex;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID viewerId = event.getUser().getUUID();
        if (viewerId == null) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            guardPassengers(event, viewerId);
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            guardDestroy(event, viewerId);
        }
    }

    private void guardPassengers(PacketSendEvent event, UUID viewerId) {
        WrapperPlayServerSetPassengers wrapper = new WrapperPlayServerSetPassengers(event);
        int[] currentPassengers = wrapper.getPassengers();
        int[] guardedPassengers = attachmentIndex.appendNametagPassenger(
                viewerId,
                wrapper.getEntityId(),
                currentPassengers
        );
        if (guardedPassengers != currentPassengers) {
            wrapper.setPassengers(guardedPassengers);
        }
    }

    private void guardDestroy(PacketSendEvent event, UUID viewerId) {
        WrapperPlayServerDestroyEntities wrapper = new WrapperPlayServerDestroyEntities(event);
        int[] currentIds = wrapper.getEntityIds();
        int[] guardedIds = attachmentIndex.appendDestroyedNametags(viewerId, currentIds);
        if (guardedIds != currentIds) {
            wrapper.setEntityIds(guardedIds);
        }
    }
}
