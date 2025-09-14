package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

import static org.bukkit.Material.LIGHT_GRAY_TERRACOTTA;
import static org.bukkit.Material.TERRACOTTA;

public class MushroomStewTank extends FoodTank {
	private static final ChatColor chatColor = ChatColor.YELLOW;

	private static final int maxAmount = 128;

	private static final long delay = 1200L;

	public MushroomStewTank(Location location, int amount, LiquidTank feature) {
		super(location, amount, 5, feature);
	}

	public static void gameLoop(LiquidTank feature) {
		feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask( () -> {
			try {
				gameTick(feature);
			} catch (Exception ignored) {
			}
		}, BukkitTime.ticks(delay), BukkitTime.ticks(delay));
	}

	private static void gameTick(LiquidTank feature) {
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (entity.getType().equals(EntityType.MOOSHROOM)) {
					if (BlockUtils.isLoaded(entity.getLocation()) &&
							entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() == Material.HOPPER) {
						Location location = entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
						Random random = new Random();
						if (random.nextInt(5) == 0) {
							AbstractTank abstractTank = feature.getTankManager().getTank(location);
							if (abstractTank != null) {
                                switch (abstractTank) {
                                    case MilkTank ignored1 when abstractTank.getQuantity() < abstractTank
                                            .getMaxQuantity() -> {
                                        abstractTank.setQuantity(abstractTank.getQuantity() + 1);
                                        abstractTank.updateVisuals();
                                        continue;
                                    }
                                    case MushroomStewTank ignored when abstractTank
                                            .getQuantity() < abstractTank.getMaxQuantity() -> {
                                        abstractTank.setQuantity(abstractTank.getQuantity() + 1);
                                        abstractTank.updateVisuals();
                                        continue;
                                    }
                                    case EmptyTank ignored -> {
                                        AbstractTank abstractTank1 = feature.getTankManager().changeTankType(abstractTank, TankType.MUSHROOM_STEW, 1);
                                        abstractTank1.updateVisuals();
                                    }
                                    default -> {
                                    }
                                }
                            }
						}
					}
					continue;
				}
				if (entity.getType().equals(EntityType.COW) &&
						BlockUtils.isLoaded(entity.getLocation()) &&
						entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() == Material.HOPPER) {
					Location location = entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
					Random random = new Random();
					if (random.nextInt(5) == 0) {
						AbstractTank abstractTank = feature.getTankManager().getTank(location);
						if (abstractTank != null) {
							if (abstractTank instanceof MilkTank && abstractTank.getQuantity() < abstractTank
									.getMaxQuantity()) {
								abstractTank.setQuantity(abstractTank.getQuantity() + 1);
								abstractTank.updateVisuals();
								continue;
							}
							if (abstractTank instanceof EmptyTank) {
								AbstractTank abstractTank1 = feature.getTankManager().changeTankType(abstractTank, TankType.MILK, 1);
								abstractTank1.updateVisuals();
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected String getLiquidHeadUrl() {
		return HeadURL.mushroomStewB64;
	}

	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.MUSHROOM_STEW) {
			if (getQuantity() + 1 <= getMaxQuantity()) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				setQuantity(getQuantity() + 1);
				updateVisuals();
			}
		} else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BOWL) {
			if (getQuantity() == 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.MUSHROOM_STEW));
				AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 1) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.MUSHROOM_STEW));
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
		return TankType.MUSHROOM_STEW;
	}

	@Override
	public int getMaxQuantity() {
		return maxAmount;
	}

	@Override
	protected void showParticles() {
		Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
		AbstractTank.spawnFallingDust(location, 20, 0.05F, 0.1F, LIGHT_GRAY_TERRACOTTA);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, TERRACOTTA);
	}
}
