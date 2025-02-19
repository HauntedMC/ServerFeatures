package nl.hauntedmc.serverfeatures.common.packet.impl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.packet.Packet;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A packet wrapper for spawning an armor stand with a custom name as a nametag.
 */
public class NametagPacket implements Packet {
    private final WrapperPlayServerSpawnEntity spawnPacket;
    private final WrapperPlayServerEntityMetadata metaPacket;
    private final WrapperPlayServerSetPassengers passengersPacket;

    public NametagPacket(Player player, Component customName, int entityID) {
        UUID uuid = UUID.randomUUID();
        Location spawnLocation = SpigotConversionUtil.fromBukkitLocation(player.getLocation().add(0, 0.15, 0));

        this.spawnPacket = new WrapperPlayServerSpawnEntity(
                entityID, uuid, EntityTypes.ARMOR_STAND, spawnLocation,
                spawnLocation.getYaw(), 0, null);

        List<EntityData> metadata = List.of(
                new EntityData(0, EntityDataTypes.BYTE, (byte) 0x20),
                new EntityData(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(customName)),
                new EntityData(3, EntityDataTypes.BOOLEAN, true));

        this.metaPacket = new WrapperPlayServerEntityMetadata(entityID, metadata);
        this.passengersPacket = new WrapperPlayServerSetPassengers(player.getEntityId(), new int[]{entityID});
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, passengersPacket);
    }
}
