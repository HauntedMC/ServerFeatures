package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import static org.bukkit.Material.NETHER_WART_BLOCK;
import static org.bukkit.Material.RED_NETHER_BRICKS;

public class BeetrootSoupTank extends FoodTank {
	private static final ChatColor chatColor = ChatColor.DARK_RED;

	private static int maxAmount = 10;

	private static final long delay = 20L;

	public BeetrootSoupTank(Location location, int amount) {
		super(location, amount, 5);
	}

	public static void setMaxAmount(int maxAmount) {
		if (maxAmount < 1)
			maxAmount = 1;
		BeetrootSoupTank.maxAmount = maxAmount;
	}

	public static void gameLoop(Plugin paramPlugin) {
		Bukkit.getScheduler().runTaskTimer(paramPlugin, BeetrootSoupTank::gameTick, delay, delay);
	}

	private static void gameTick() {
		//
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
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
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
	public ChatColor getChatColor() {
		return chatColor;
	}

	@Override
	public TankType getTankType() {
		return TankType.BEETROOT_SOUP;
	}

	@Override
	public int getMaxQuantity() {
		return maxAmount;
	}

	@Override
	protected void showParticles() {
		Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
		AbstractTank.spawnFallingDust(location, 20, 0.05F, 0.1F, NETHER_WART_BLOCK);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, RED_NETHER_BRICKS);
	}
}
