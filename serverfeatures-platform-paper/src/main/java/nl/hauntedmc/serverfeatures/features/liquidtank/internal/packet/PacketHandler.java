package nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

public class PacketHandler {
    // Keep client-only entities in the negative range so they cannot collide with server entities.
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(-1);

    private final Location location;
    private final int entityID;
    private ItemStack head;
    private ClearPacket clearPacket;
    private ArmourStandPacket showPacket;

    public PacketHandler(Location location) {
        this.location = location.clone();
        this.entityID = NEXT_ENTITY_ID.getAndDecrement();
    }

    public void setHead(ItemStack bukkitItemStack) {
        this.head = bukkitItemStack.clone();
        this.showPacket = null;
    }

    public void show(Player player) {
        if (head == null) {
            return;
        }
        if (showPacket == null) {
            Location spawnLocation = location.clone().add(0, -0.35, 0);
            showPacket = new ArmourStandPacket(spawnLocation, entityID, head);
        }
        showPacket.sendTo(player);
    }

    public void hide(Player player) {
        if (showPacket == null) {
            return;
        }
        if (clearPacket == null) {
            clearPacket = new ClearPacket(entityID);
        }
        clearPacket.sendTo(player);
    }

    int entityId() {
        return entityID;
    }
}
