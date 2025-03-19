package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class DragonBreathTank extends AbstractTank {
	private static final TankType type = TankType.DRAGON_BREATH;

	private static final ChatColor chatColor = ChatColor.LIGHT_PURPLE;

	private static int maxAmount = 30;

	private static final long delay = 100L;

	public DragonBreathTank(Location location, int amount) {
		super(location, amount);
	}

	public static TankType getType() {
		return type;
	}

	public static void setMaxAmount(int paramInt) {
		if (paramInt < 1)
			paramInt = 1;
		maxAmount = paramInt;
	}

	public static void gameLoop(Plugin paramPlugin) {
		Bukkit.getScheduler().runTaskTimer(paramPlugin, DragonBreathTank::gameTick, delay, delay);
	}

	private static void gameTick() {
		//
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
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
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
	public ChatColor getChatColor() {
		return chatColor;
	}

	@Override
	public TankType getTankType() {
		return TankType.DRAGON_BREATH;
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
