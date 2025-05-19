package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public abstract class FoodTank extends AbstractTank {
	private static final ChatColor chatColor = ChatColor.DARK_GRAY;

	private static final int maxAmount = 128;

	private static final long delay = 30L;

	private final int saturationAmplifier;

	public FoodTank(Location location, int amount, int saturationAmplifier, LiquidTank feature) {
		super(location, amount, feature);
		this.saturationAmplifier = saturationAmplifier;
	}

	public static void gameLoop(LiquidTank feature) {
		feature.getLifecycleManager().getTaskManager().scheduleDelayedRepeatingTask( () -> {
			try {
				gameTick(feature);
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}, delay, delay);
	}

	private static void gameTick(LiquidTank feature) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getFoodLevel() < 20 && (player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode().equals(GameMode.ADVENTURE))) {
				Block block = player.getLocation().add(0.0D, 2.75D, 0.0D).getBlock();
				if (block.getType() == Material.HOPPER) {
					AbstractTank abstractTank = feature.getTankManager().getTank(block.getLocation());
					if (abstractTank != null && abstractTank instanceof FoodTank foodTank) {
                        foodTank.saturatePlayer(player);
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

	protected void saturatePlayer(Player player) {
		showParticles();
		player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 1, saturationAmplifier));
	}

	@Override
	public void onInteract(Player paramPlayer) {
		playTitle(paramPlayer);
	}

	@Override
	public ChatColor getChatColor() {
		return chatColor;
	}

	@Override
	public TankType getTankType() {
		return TankType.EMPTY;
	}

	@Override
	public int getMaxQuantity() {
		return maxAmount;
	}
}
