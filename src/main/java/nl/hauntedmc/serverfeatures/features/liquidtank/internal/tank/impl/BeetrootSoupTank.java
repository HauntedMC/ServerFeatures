package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.NETHER_WART_BLOCK;
import static org.bukkit.Material.RED_NETHER_BRICKS;

public class BeetrootSoupTank extends FoodTank {
    public BeetrootSoupTank(Location location, int amount, LiquidTank feature) {
		super(location, amount, 5, feature);
	}

	@Override
	protected String getLiquidHeadUrl() {
		return HeadURL.beetrootB64;
	}
	
	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BEETROOT_SOUP) {
			if (getQuantity() + 1 <= getMaxQuantity()) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				setQuantity(getQuantity() + 1);
				updateVisuals();
			}
		} else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BOWL) {
			if (getQuantity() == 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BEETROOT_SOUP));
				AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BEETROOT_SOUP));
				setQuantity(getQuantity() - 1);
				updateVisuals();
			}
		}
		playTitle(paramPlayer);
	}

	@Override
	public String getChatColor() {
		return "&4";
	}

	@Override
	public TankType getTankType() {
		return TankType.BEETROOT_SOUP;
	}

	@Override
	public int getMaxQuantity() {
        return 128;
	}

	@Override
	protected void showParticles() {
		Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
		AbstractTank.spawnFallingDust(location, 20, 0.05F, 0.1F, NETHER_WART_BLOCK);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, RED_NETHER_BRICKS);
	}
}
