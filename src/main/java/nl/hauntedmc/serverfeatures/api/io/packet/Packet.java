package nl.hauntedmc.serverfeatures.api.io.packet;

import org.bukkit.entity.Player;

/**
 * Interface for all packet types, enabling abstraction from PacketEvents or other libraries.
 */
public interface Packet {
    void sendTo(Player player);
}
