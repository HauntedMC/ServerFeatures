package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.QUARTZ_BLOCK;

public class MilkTank extends AbstractTank {
    private static final int maxAmount = 128;

    public MilkTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    @Override
    protected String getLiquidHeadUrl() {
        return HeadURL.milkB64;
    }

    @Override
    public void onInteract(Player paramPlayer) {
        if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.MILK_BUCKET) {
            if (getQuantity() + 3 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
                setQuantity(getQuantity() + 3);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BUCKET) {
            if (getQuantity() == 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.MILK_BUCKET));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.MILK_BUCKET));
                setQuantity(getQuantity() - 3);
                updateVisuals();
            }
        }
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&f";
    }

    @Override
    public TankType getTankType() {
        return TankType.MILK;
    }

    @Override
    public int getMaxQuantity() {
        return maxAmount;
    }

    @Override
    protected void showParticles() {
        AbstractTank.spawnFallingDust(getLocation().clone().add(0.5D, 0.0D, 0.5D), 30, 0.05F, 0.1F, QUARTZ_BLOCK);
    }
}
