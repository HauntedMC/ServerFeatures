package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import nl.hauntedmc.serverfeatures.api.io.packet.Packet;
import org.bukkit.entity.Player;


/**
 * A packet wrapper for spawning an armor stand with a custom name as a nametag.
 */
public class MountNametagEntityPacket implements Packet {
    private final WrapperPlayServerSetPassengers passengersPacket;

    public MountNametagEntityPacket(Player player, int[] passenger) {
        this.passengersPacket = new WrapperPlayServerSetPassengers(player.getEntityId(), passenger);
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, passengersPacket);
    }
}
