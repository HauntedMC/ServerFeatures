package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import nl.hauntedmc.serverfeatures.api.io.packet.Packet;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * A packet wrapper for spawning an armor stand with a custom name as a nametag.
 */
public class CreateNametagEntityPacket implements Packet {
    private final WrapperPlayServerSpawnEntity spawnPacket;
    private final WrapperPlayServerEntityMetadata metaPacket;

    public CreateNametagEntityPacket(Player player, int entityID, List<EntityData<?>> metaData) {
        Location spawnLocation = SpigotConversionUtil.fromBukkitLocation(player.getLocation().add(0, 1.8, 0));
        this.spawnPacket = new WrapperPlayServerSpawnEntity(
                entityID,
                UUID.randomUUID(),
                EntityTypes.TEXT_DISPLAY,
                spawnLocation,
                spawnLocation.getYaw(), 0, null);
        this.metaPacket = new WrapperPlayServerEntityMetadata(entityID, metaData);
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
    }
}
