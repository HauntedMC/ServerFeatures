package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Random;

public class LavaTank extends AbstractTank {
	private static final TankType type = TankType.LAVA;

	private static final ChatColor chatColor = ChatColor.RED;

	private static int maxAmount = 30;

	private static final long delay = 100L;

	private static final long delayFill = 6000L;

	public LavaTank(Location location, int amount) {
		super(location, amount);
	}

	public static TankType getType() {
		return type;
	}

	public static void setMaxAmount(int paramInt) {
		if (paramInt < 3)
			paramInt = 3;
		maxAmount = paramInt;
	}

	public static void gameLoop(Plugin paramPlugin) {
		Bukkit.getScheduler().runTaskTimer(paramPlugin, () -> {
			try {
				gameTick();
			} catch (Exception exception) {
			}
		}, delay, delay);
		Bukkit.getScheduler().runTaskTimer(paramPlugin, () -> {
			try {
				fillTick();
			} catch (Exception exception) {
			}
		}, delayFill, delayFill);
	}

	private static void gameTick() {
		for (AbstractTank abstractTank : LiquidTanks.tankManager.getTankList()) {
			if (abstractTank instanceof LavaTank || abstractTank instanceof EmptyTank) {
				if (abstractTank instanceof LavaTank &&
						BlockUtils.isLoaded(abstractTank.getLocation())) {
					ArrayList<Block> arrayList = new ArrayList<>();
					arrayList.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.DOWN));
					arrayList.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.SOUTH));
					arrayList.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.NORTH));
					arrayList.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.WEST));
					arrayList.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.EAST));
					for (Block block : arrayList) {
						if (abstractTank.getQuantity() == 0)
							return;
						if (block.getState() instanceof BlastFurnace) {
							BlastFurnace blastFurnace = (BlastFurnace) block.getState();
							ItemStack itemStack = null;
							try {
								itemStack = blastFurnace.getInventory().getItem(0);
							} catch (Exception exception) {
							}
							if (itemStack != null && blastFurnace.getBurnTime() == 0) {
								blastFurnace.setBurnTime((short) 6667);
								blastFurnace.update();
								if (abstractTank.getQuantity() > 1) {
									abstractTank.setQuantity(abstractTank.getQuantity() - 1);
									abstractTank.updateVisuals();
								} else if (abstractTank.getQuantity() == 1) {
									LiquidTanks.tankManager.emptyTank(abstractTank);
									break;
								}
							}
						} else if (block.getState() instanceof Smoker) {
							Smoker smoker = (Smoker) block.getState();
							ItemStack itemStack = null;
							try {
								itemStack = smoker.getInventory().getItem(0);
							} catch (Exception exception) {
							}
							if (itemStack != null && smoker.getBurnTime() == 0) {
								smoker.setBurnTime((short) 6667);
								smoker.update();
								if (abstractTank.getQuantity() > 1) {
									abstractTank.setQuantity(abstractTank.getQuantity() - 1);
									abstractTank.updateVisuals();
								} else if (abstractTank.getQuantity() == 1) {
									LiquidTanks.tankManager.emptyTank(abstractTank);
									break;
								}
							}
						}
						if (block.getType() == Material.FURNACE) {
							Furnace furnace = (Furnace) block.getState();
							ItemStack itemStack = null;
							try {
								itemStack = furnace.getInventory().getItem(0);
							} catch (Exception exception) {
							}
							if (itemStack != null && furnace.getBurnTime() == 0) {
								furnace.setBurnTime((short) 6667);
								furnace.update();
								if (abstractTank.getQuantity() > 1) {
									abstractTank.setQuantity(abstractTank.getQuantity() - 1);
									abstractTank.updateVisuals();
									continue;
								}
								if (abstractTank.getQuantity() == 1) {
									LiquidTanks.tankManager.emptyTank(abstractTank);
									break;
								}
							}
						}
					}
				}
				if (abstractTank.getQuantity() < abstractTank.getMaxQuantity() &&
						BlockUtils.isLoaded(abstractTank.getLocation())) {
					Block block = abstractTank.getLocation().clone().add(0.0D, 1.0D, 0.0D).getBlock();
					if (isFullLava(block)) {
						block.setType(Material.AIR);
						if (abstractTank instanceof EmptyTank) {
							LiquidTanks.tankManager.changeTankType(abstractTank, TankType.LAVA, 3);
							continue;
						}
						abstractTank.setQuantity(Math.min(abstractTank.getQuantity() + 3, abstractTank.getMaxQuantity()));
						abstractTank.updateVisuals();
					}
				}
			}
		}
	}

	private static void fillTick() {
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (entity.getType().equals(EntityType.MAGMA_CUBE) &&
						BlockUtils.isLoaded(entity.getLocation())) {
					MagmaCube magmaCube = (MagmaCube) entity;
					if (magmaCube.getSize() == 2 && entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() == Material.HOPPER) {
						Location location = entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
						Random random = new Random();
						if (random.nextInt(3) == 0) {
							AbstractTank abstractTank = LiquidTanks.tankManager.getTank(location);
							addLava(abstractTank);
						}
					}
					if (magmaCube.getSize() == 4) {
						ArrayList<Block> arrayList = new ArrayList<>();
						arrayList.add(entity.getLocation().add(0.5D, -1.0D, 0.5D).getBlock());
						arrayList.add(entity.getLocation().add(-0.5D, -1.0D, 0.5D).getBlock());
						arrayList.add(entity.getLocation().add(-0.5D, -1.0D, -0.5D).getBlock());
						arrayList.add(entity.getLocation().add(0.5D, -1.0D, -0.5D).getBlock());
						for (Block block : arrayList) {
							if (block.getType() == Material.HOPPER) {
								Random random = new Random();
								if (random.nextInt(3) == 0) {
									AbstractTank abstractTank = LiquidTanks.tankManager.getTank(block.getLocation());
									addLava(abstractTank);
								}
							}
						}
					}
				}
			}
		}
	}

	private static void addLava(AbstractTank paramAbstractTank) {
		if (paramAbstractTank != null)
			if (paramAbstractTank instanceof LavaTank && paramAbstractTank.getQuantity() + 1 <= paramAbstractTank.getMaxQuantity()) {
				paramAbstractTank.setQuantity(paramAbstractTank.getQuantity() + 1);
				paramAbstractTank.updateVisuals();
			} else if (paramAbstractTank instanceof EmptyTank) {
				AbstractTank abstractTank = LiquidTanks.tankManager.changeTankType(paramAbstractTank, TankType.LAVA, 1);
				abstractTank.updateVisuals();
			}
	}

	private static boolean isFullLava(Block block) {
		return block.getType() == Material.LAVA;
	}

	@Override
	protected String getLiquidHeadUrl() {
		return HeadURL.lavaB64;
	}

	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.LAVA_BUCKET) {
			if (getQuantity() + 3 <= getMaxQuantity()) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
				setQuantity(getQuantity() + 3);
				updateVisuals();
			}
		} else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BUCKET) {
			if (getQuantity() == 3) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.LAVA_BUCKET));
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 3) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.LAVA_BUCKET));
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
		return TankType.LAVA;
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
