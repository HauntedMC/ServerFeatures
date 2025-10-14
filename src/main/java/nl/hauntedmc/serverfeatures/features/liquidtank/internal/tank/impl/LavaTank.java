package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LavaTank extends AbstractTank {
    private static final TankType type = TankType.LAVA;

    private static final int maxAmount = 128;

    public LavaTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    public static TankType getType() {
        return type;
    }

    @Override
    protected String getLiquidHeadUrl() {
        return HeadURL.lavaB64;
    }

    @Override
    public void onInteract(Player paramPlayer) {
        if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.LAVA_BUCKET) {
            if (getQuantity() + 3 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
                setQuantity(getQuantity() + 3);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BUCKET) {
            if (getQuantity() == 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.LAVA_BUCKET));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.LAVA_BUCKET));
                setQuantity(getQuantity() - 3);
                updateVisuals();
            }
        }
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&c";
    }

    @Override
    public TankType getTankType() {
        return TankType.LAVA;
    }

    @Override
    public int getMaxQuantity() {
        return maxAmount;
    }

    @Override
    protected void showParticles() {
        // this tank doesn't have particles
    }
}
