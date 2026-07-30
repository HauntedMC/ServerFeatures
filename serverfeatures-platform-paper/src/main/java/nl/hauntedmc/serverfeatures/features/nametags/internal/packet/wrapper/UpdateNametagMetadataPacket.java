package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import nl.hauntedmc.serverfeatures.api.io.packet.Packet;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Updates an already-spawned text display without destroying or recreating it.
 */
public final class UpdateNametagMetadataPacket implements Packet {
    private final WrapperPlayServerEntityMetadata metadataPacket;

    public UpdateNametagMetadataPacket(int entityId, List<EntityData<?>> metadata) {
        this.metadataPacket = new WrapperPlayServerEntityMetadata(entityId, List.copyOf(metadata));
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metadataPacket);
    }
}
