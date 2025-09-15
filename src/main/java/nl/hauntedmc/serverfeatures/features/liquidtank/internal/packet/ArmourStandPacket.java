package nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import nl.hauntedmc.serverfeatures.common.packet.Packet;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArmourStandPacket implements Packet {
    private final WrapperPlayServerSpawnEntity spawnPacket;
    private final WrapperPlayServerEntityMetadata metaPacket;
    private final WrapperPlayServerEntityEquipment equipmentPacket;

    public ArmourStandPacket(org.bukkit.Location loc, int entityID, ItemStack head) {
        UUID uuid = UUID.randomUUID();
        Location spawnLocation = SpigotConversionUtil.fromBukkitLocation(loc);

        this.spawnPacket = new WrapperPlayServerSpawnEntity(
                entityID, uuid, EntityTypes.ARMOR_STAND, spawnLocation,
                spawnLocation.getYaw(), 0, null);

        List<EntityData<?>> metadata = List.of(
                new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20),
                new EntityData<>(5, EntityDataTypes.BOOLEAN, true),
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) (0x01 | 0x10))
        );

        this.metaPacket = new WrapperPlayServerEntityMetadata(entityID, metadata);

        List<Equipment> equipmentList = new ArrayList<>();
        equipmentList.add(new Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(head)));
        this.equipmentPacket = new WrapperPlayServerEntityEquipment(entityID, equipmentList);
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, equipmentPacket);
    }
}
