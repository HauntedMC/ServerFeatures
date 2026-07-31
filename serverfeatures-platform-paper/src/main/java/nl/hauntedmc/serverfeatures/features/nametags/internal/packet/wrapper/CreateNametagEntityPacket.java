package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import nl.hauntedmc.serverfeatures.api.io.packet.Packet;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Spawns one initially empty client-side text display with a stable identity for the current
 * nametag generation. Metadata is deliberately sent only after the passenger mount packet so a
 * delayed or rejected mount cannot briefly reveal a floating nametag at its spawn position.
 */
public final class CreateNametagEntityPacket implements Packet {
    private final WrapperPlayServerSpawnEntity spawnPacket;

    public CreateNametagEntityPacket(Player owner, int entityId, UUID entityUuid) {
        org.bukkit.Location bukkitLocation = owner.getLocation().clone();
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
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
    }
}
