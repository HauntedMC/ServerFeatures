package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.*;

public class HoneyTank extends FoodTank {
    private static final int maxAmount = 128;

    public HoneyTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    @Override
    protected String getLiquidHeadUrl() {
        return HeadURL.honeyB64;
    }

    @Override
    public void onInteract(Player paramPlayer) {
        if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.HONEY_BOTTLE) {
            if (getQuantity() + 1 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
                setQuantity(getQuantity() + 1);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
            if (getQuantity() == 1) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.HONEY_BOTTLE));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 1) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.HONEY_BOTTLE));
                setQuantity(getQuantity() - 1);
                updateVisuals();
            }
        }
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&6";
    }

    @Override
    public TankType getTankType() {
        return TankType.HONEY;
    }

    @Override
    public int getMaxQuantity() {
        return maxAmount;
    }

    @Override
    protected void showParticles() {
        Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
        AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, HONEY_BLOCK);
        AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, YELLOW_CONCRETE);
        AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, ORANGE_CONCRETE);
    }
}
