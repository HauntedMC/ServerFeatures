package nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class PacketHandler {
    private final Location location;
    private final Random random;
    private int entityID;
    private ItemStack head;

    public PacketHandler(Location location) {
        this.location = location.clone();
        this.random = new Random();
    }

    public void setHead(ItemStack bukkitItemStack) {
        this.head = bukkitItemStack;
    }

    public void show(Player player) {
        // Generate a random positive entity ID for this tank
        this.entityID = random.nextInt(Integer.MAX_VALUE);
        Location loc = location.clone();
        // Adjust the location as required (e.g., lower by 0.35 blocks)
        loc.add(0, -0.35, 0);
        ArmourStandPacket armourStandPacket = new ArmourStandPacket(loc, entityID, head);
        armourStandPacket.sendTo(player);
    }

    public void hide(Player player) {
        ClearPacket clearPacket = new ClearPacket(entityID);
        clearPacket.sendTo(player);
    }
}
