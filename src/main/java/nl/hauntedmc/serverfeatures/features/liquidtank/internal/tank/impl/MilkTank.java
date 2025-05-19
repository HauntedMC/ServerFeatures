package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import static org.bukkit.Material.QUARTZ_BLOCK;

public class MilkTank extends AbstractTank {
	private static final ChatColor chatColor = ChatColor.WHITE;

	private static final int maxAmount = 128;

	private static final long delay = 20L;

	public MilkTank(Location location, int amount, LiquidTank feature) {
		super(location, amount, feature);
	}

	public static void gameLoop(LiquidTank feature) {
		feature.getLifecycleManager().getTaskManager().scheduleDelayedRepeatingTask( () -> {
			try {
				gameTick(feature);
			} catch (Exception exception) {
			}
		}, delay, delay);
	}

	private static void gameTick(LiquidTank feature) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!player.getActivePotionEffects().isEmpty() && (player.getGameMode().equals(GameMode.SURVIVAL) || player
					.getGameMode().equals(GameMode.ADVENTURE))) {
				Block block = player.getLocation().add(0.0D, 2.75D, 0.0D).getBlock();
				if (block.getType() == Material.HOPPER) {
					AbstractTank abstractTank = feature.getTankManager().getTank(block.getLocation());
					if (abstractTank != null && abstractTank instanceof MilkTank) {
						abstractTank.showParticles();
						for (PotionEffect potionEffect : player.getActivePotionEffects())
							player.removePotionEffect(potionEffect.getType());
						abstractTank.setQuantity(abstractTank.getQuantity() - 1);
						if (abstractTank.getQuantity() == 0) {
							feature.getTankManager().emptyTank(abstractTank);
							continue;
						}
						abstractTank.updateVisuals();
					}
				}
			}
		}
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
	public ChatColor getChatColor() {
		return chatColor;
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
		AbstractTank.spawnFallingDust(getLocation().clone().add(0.5D, 0.0D, 0.5D), 30,  0.05F, 0.1F, QUARTZ_BLOCK);
	}
}
