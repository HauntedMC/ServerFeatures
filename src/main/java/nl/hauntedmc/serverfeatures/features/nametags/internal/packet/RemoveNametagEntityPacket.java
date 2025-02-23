package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import nl.hauntedmc.serverfeatures.common.packet.Packet;
import org.bukkit.entity.Player;

/**
 * A packet wrapper for spawning an armor stand with a custom name as a nametag.
 */
public class RemoveNametagEntityPacket implements Packet {
    private final WrapperPlayServerDestroyEntities destroyEntities;

    public RemoveNametagEntityPacket(int entityID) {
        this.destroyEntities = new WrapperPlayServerDestroyEntities(entityID);
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyEntities);
    }
}
