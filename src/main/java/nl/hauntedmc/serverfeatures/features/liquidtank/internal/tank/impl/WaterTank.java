package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.PotionUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import static org.bukkit.Material.*;
import static org.bukkit.Particle.SPLASH;

public class WaterTank extends AbstractTank {
    private static final TankType type = TankType.WATER;

    private static final int maxAmount = 128;

    public WaterTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
    }

    @Override
    protected String getLiquidHeadUrl() {
        return HeadURL.waterB64;
    }

    @Override
    public void onInteract(Player paramPlayer) {
        if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.WATER_BUCKET) {
            if (getQuantity() + 3 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
                setQuantity(getQuantity() + 3);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BUCKET) {
            if (getQuantity() == 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.WATER_BUCKET));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 3) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.WATER_BUCKET));
                setQuantity(getQuantity() - 3);
                updateVisuals();
            }
        } else if (PotionUtils.isWaterBottle(paramPlayer.getInventory().getItemInMainHand())) {
            if (getQuantity() + 1 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
                setQuantity(getQuantity() + 1);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
            if (getQuantity() == 1) {
                ItemStack itemStack = new ItemStack(Material.POTION, 1);
                ItemMeta itemMeta = itemStack.getItemMeta();
                PotionMeta potionMeta = (PotionMeta) itemMeta;
                PotionType potionData = PotionType.WATER;
                potionMeta.setBasePotionType(potionData);
                itemStack.setItemMeta(itemMeta);
                changeItemFromPlayer(paramPlayer, new ItemStack(itemStack));
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                abstractTank.updateVisuals();
                return;
            }
            if (getQuantity() > 1) {
                ItemStack itemStack = new ItemStack(Material.POTION, 1);
                ItemMeta itemMeta = itemStack.getItemMeta();
                PotionMeta potionMeta = (PotionMeta) itemMeta;
                PotionType potionData = PotionType.WATER;
                potionMeta.setBasePotionType(potionData);
                itemStack.setItemMeta(itemMeta);
                changeItemFromPlayer(paramPlayer, new ItemStack(itemStack));
                setQuantity(getQuantity() - 1);
                updateVisuals();
            }
        }
        playTitle(paramPlayer);
    }

    @Override
    public String getChatColor() {
        return "&b";
    }

    @Override
    public TankType getTankType() {
        return type;
    }

    @Override
    public int getMaxQuantity() {
        return maxAmount;
    }

    @Override
    protected void showParticles() {
        Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
        spawnFallingDust(location, 10, 0.05F, 0.1F, LIGHT_BLUE_WOOL);
        spawnFallingDust(location, 10, 0.05F, 0.1F, LIGHT_BLUE_CONCRETE);
        spawnFallingDust(location, 10, 0.05F, 0.1F, BLUE_CONCRETE);
    }

}
