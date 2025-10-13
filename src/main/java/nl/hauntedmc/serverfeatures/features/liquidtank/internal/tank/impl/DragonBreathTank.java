package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DragonBreathTank extends AbstractTank {
    private static final TankType type = TankType.DRAGON_BREATH;

    public DragonBreathTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    public static TankType getType() {
        return type;
    }

    @Override
    protected String getLiquidHeadUrl() {
        return HeadURL.dragonBreathB64;
    }

    @Override
    public void onInteract(Player paramPlayer) {
        if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.DRAGON_BREATH) {
            if (getQuantity() + 1 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
                setQuantity(getQuantity() + 1);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
            if (getQuantity() == 1) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.DRAGON_BREATH));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 1) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.DRAGON_BREATH));
                setQuantity(getQuantity() - 1);
                updateVisuals();
            }
        }
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&d";
    }

    @Override
    public TankType getTankType() {
        return TankType.DRAGON_BREATH;
    }

    @Override
    public int getMaxQuantity() {
        return 128;
    }

    @Override
    protected void showParticles() {
        // this tank doesn't have particles
    }
}
