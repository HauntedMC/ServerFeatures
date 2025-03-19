package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

import static org.bukkit.Material.*;

public class HoneyTank extends FoodTank {
	private static final ChatColor chatColor = ChatColor.GOLD;

	private static int maxAmount = 30;

	private static final long delay = 20L;

	public HoneyTank(Location location, int amount) {
		super(location, amount, 4);
	}

	public static void setMaxAmount(int paramInt) {
		if (paramInt < 1)
			paramInt = 1;
		maxAmount = paramInt;
	}

	public static void gameLoop(Plugin paramPlugin) {
		Bukkit.getScheduler().runTaskTimer(paramPlugin, HoneyTank::gameTick, delay, delay);
	}

	private static void gameTick() {
		try {
			Method method1 = Block.class.getMethod("getBlockData", new Class[0]);
			Method method2 = Block.class.getMethod("setBlockData", new Class[] { BlockData.class });
			for (AbstractTank abstractTank : LiquidTanks.tankManager.getTankList()) {
				if ((abstractTank instanceof HoneyTank || abstractTank instanceof EmptyTank) &&
						abstractTank.getQuantity() < abstractTank.getMaxQuantity() &&
						BlockUtils.isLoaded(abstractTank.getLocation())) {
					Block block = abstractTank.getLocation().clone().add(0.0D, 1.0D, 0.0D).getBlock();
					if (method1.invoke(block, new Object[0]) instanceof Beehive) {
						Beehive beehive = (Beehive) method1.invoke(block, new Object[0]);
						if (beehive.getHoneyLevel() > 0) {
							beehive.setHoneyLevel(beehive.getHoneyLevel() - 1);
							method2.invoke(block, new Object[] { beehive });
							if (abstractTank instanceof EmptyTank) {
								LiquidTanks.tankManager.changeTankType(abstractTank, TankType.HONEY, 1);
								continue;
							}
							abstractTank.setQuantity(abstractTank.getQuantity() + 1);
							abstractTank.updateVisuals();
						}
					}
				}
			}
		} catch (Exception exception) {
		}
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
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
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
	public ChatColor getChatColor() {
		return chatColor;
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
