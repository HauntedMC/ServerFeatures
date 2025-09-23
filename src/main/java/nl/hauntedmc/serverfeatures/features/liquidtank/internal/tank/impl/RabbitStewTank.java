package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.*;

public class RabbitStewTank extends FoodTank {
	private static final int maxAmount = 128;

	public RabbitStewTank(Location location, int amount, LiquidTank feature) {
		super(location, amount, 12, feature);
	}

	@Override
	protected String getLiquidHeadUrl() {
		return HeadURL.rabbitStewB64;
	}

	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.RABBIT_STEW) {
			if (getQuantity() + 1 <= getMaxQuantity()) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				setQuantity(getQuantity() + 1);
				updateVisuals();
			}
		} else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BOWL) {
			if (getQuantity() == 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.RABBIT_STEW));
				AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.RABBIT_STEW));
				setQuantity(getQuantity() - 1);
				updateVisuals();
			}
		}
		playTitle(paramPlayer);
	}

	@Override
	public String getChatColor() {
		return "&e";
	}

	@Override
	public TankType getTankType() {
		return TankType.RABBIT_STEW;
	}

	@Override
	public int getMaxQuantity() {
		return maxAmount;
	}

	@Override
	protected void showParticles() {
		Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, ORANGE_TERRACOTTA);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, TERRACOTTA);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, LIGHT_GRAY_TERRACOTTA);
	}
}
