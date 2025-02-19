package nl.hauntedmc.serverfeatures.common.packet;

import org.bukkit.entity.Player;

/**
 * Manages sending packets to players, allowing unicast, multicast, and broadcast.
 */
public class PacketManager {

    /**
     * Sends packets to a single player.
     *
     * @param player The recipient player.
     * @param packets The packets to send.
     */
    public static void sendUnicast(Player player, Packet... packets) {
        for (Packet packet : packets) {
            packet.sendTo(player);
        }
    }

    /**
     * Sends packets to multiple players in range of a specific player.
     *
     * @param player The center player.
     * @param range The max range to send to.
     * @param packets The packets to send.
     */
    public static void sendMulticast(Player player, double range, Packet... packets) {
        for (Player nearby : player.getWorld().getPlayers()) {
            if (!nearby.equals(player) && nearby.getLocation().distance(player.getLocation()) <= range) {
                for (Packet packet : packets) {
                    packet.sendTo(nearby);
                }
            }
        }
    }

    /**
     * Sends packets to all online players.
     *
     * @param packets The packets to send.
     */
    public static void sendBroadcast(Packet... packets) {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            for (Packet packet : packets) {
                packet.sendTo(player);
            }
        }
    }
}