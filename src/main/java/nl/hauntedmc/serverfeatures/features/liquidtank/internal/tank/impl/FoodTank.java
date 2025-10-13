package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public abstract class FoodTank extends AbstractTank {
    private static final int maxAmount = 128;

    public FoodTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    @Override
    public void onInteract(Player paramPlayer) {
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&9";
    }

    @Override
    public int getMaxQuantity() {
        return maxAmount;
    }
}
