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
 * Spawns one client-side text display with a stable identity for the current nametag generation.
 */
public final class CreateNametagEntityPacket implements Packet {
    private final WrapperPlayServerSpawnEntity spawnPacket;
    private final WrapperPlayServerEntityMetadata metaPacket;

    public CreateNametagEntityPacket(
            Player owner,
            int entityId,
            UUID entityUuid,
            List<EntityData<?>> metadata
    ) {
        org.bukkit.Location bukkitLocation = owner.getLocation().clone().add(0.0, 1.8, 0.0);
        Location spawnLocation = SpigotConversionUtil.fromBukkitLocation(bukkitLocation);
        this.spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                entityUuid,
                EntityTypes.TEXT_DISPLAY,
                spawnLocation,
                spawnLocation.getYaw(),
                0,
                null
        );
        this.metaPacket = new WrapperPlayServerEntityMetadata(entityId, List.copyOf(metadata));
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
    }
}
