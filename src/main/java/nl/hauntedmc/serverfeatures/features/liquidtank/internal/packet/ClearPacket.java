package nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import nl.hauntedmc.serverfeatures.api.io.packet.Packet;
import org.bukkit.entity.Player;

/**
 * A packet wrapper for spawning an armor stand with a custom name as a nametag.
 */
public class ClearPacket implements Packet {
    private final WrapperPlayServerDestroyEntities destroyEntities;

    public ClearPacket(int entityID) {
        this.destroyEntities = new WrapperPlayServerDestroyEntities(entityID);
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyEntities);
    }
}