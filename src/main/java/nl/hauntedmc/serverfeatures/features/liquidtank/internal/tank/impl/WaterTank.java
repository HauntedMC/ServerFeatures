package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;

import static org.bukkit.Material.*;
import static org.bukkit.Particle.SPLASH;

public class WaterTank extends AbstractTank {
	private static final TankType type = TankType.WATER;

	private static final ChatColor chatColor = ChatColor.AQUA;

	private static int maxAmount = 30;

	private static final long delay = 20L;

	public WaterTank(Location location, int amount) {
		super(location, amount);
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
	}

	private static void gameTick() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getFireTicks() > 0 && (player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode().equals(GameMode.ADVENTURE))) {
				Block block = player.getLocation().add(0.0D, 2.75D, 0.0D).getBlock();
				if (block.getType() == Material.HOPPER && (!LiquidTanks.settings.isPowerRequired() || block.isBlockPowered() || block.isBlockIndirectlyPowered())) {
					AbstractTank abstractTank = LiquidTanks.tankManager.getTank(block.getLocation());
					if (abstractTank != null && abstractTank instanceof WaterTank) {
						abstractTank.showParticles();
						player.setFireTicks(0);
						abstractTank.setQuantity(abstractTank.getQuantity() - 1);
						if (abstractTank.getQuantity() == 0) {
							LiquidTanks.tankManager.emptyTank(abstractTank);
							continue;
						}
						abstractTank.updateVisuals();
					}
				}
			}
		}
		if (LiquidTanks.settings.isFountainEnabled())
			for (AbstractTank abstractTank : LiquidTanks.tankManager.getTankList()) {
				if (abstractTank instanceof WaterTank && BlockUtils.isLoaded(abstractTank.getLocation())) {
					WaterTank tank = (WaterTank) abstractTank;
					ArrayList<Block> arrayList1 = new ArrayList<>();
					arrayList1.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.SOUTH));
					arrayList1.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.NORTH));
					arrayList1.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.WEST));
					arrayList1.add(abstractTank.getLocation().getBlock().getRelative(BlockFace.EAST));
					ArrayList<Block> arrayList2 = new ArrayList<>();
					for (Block block : arrayList1) {
						if (block.getType() == Material.DISPENSER && ((Directional) block.getBlockData()).getFacing() == BlockFace.UP && (block.isBlockPowered() || block.isBlockIndirectlyPowered()))
							arrayList2.add(block);
					}
					if (arrayList2.size() > 0 && arrayList2.size() < 3) {
						for (Block block : arrayList2) {
							if (arrayList2.size() > 1) {
								tank.showFountainParticles(block.getLocation(), 1.5D, 5, 1.0F);
								continue;
							}
							tank.showFountainParticles(block.getLocation(), 2.0D, 7, 1.5F);
						}
						if (!isFullWater(abstractTank.getLocation().getBlock().getRelative(BlockFace.UP))) {
							abstractTank.setQuantity(abstractTank.getQuantity() - 1);
							if (abstractTank.getQuantity() == 0) {
								LiquidTanks.tankManager.emptyTank(abstractTank);
								continue;
							}
							abstractTank.updateVisuals();
						}
					}
				}
			}
		for (AbstractTank abstractTank : LiquidTanks.tankManager.getTankList()) {
			if ((abstractTank instanceof WaterTank || abstractTank instanceof EmptyTank) &&
					BlockUtils.isLoaded(abstractTank.getLocation()) && abstractTank.getQuantity() < abstractTank.getMaxQuantity()) {
				Block block = abstractTank.getLocation().clone().add(0.0D, 1.0D, 0.0D).getBlock();
				if (isFullWater(block)) {
					block.setType(Material.AIR);
					if (abstractTank instanceof EmptyTank) {
						LiquidTanks.tankManager.changeTankType(abstractTank, type, 3);
						continue;
					}
					abstractTank.setQuantity(Math.min(abstractTank.getQuantity() + 3, abstractTank.getMaxQuantity()));
					abstractTank.updateVisuals();
				}
			}
		}
	}

	private static boolean isFullWater(Block block) {
		return block.getType() == Material.WATER;
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
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
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
				PotionData potionData = new PotionData(PotionType.WATER);
				potionMeta.setBasePotionData(potionData);
				itemStack.setItemMeta(itemMeta);
				changeItemFromPlayer(paramPlayer, new ItemStack(itemStack));
				AbstractTank abstractTank = LiquidTanks.tankManager.emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 1) {
				ItemStack itemStack = new ItemStack(Material.POTION, 1);
				ItemMeta itemMeta = itemStack.getItemMeta();
				PotionMeta potionMeta = (PotionMeta) itemMeta;
				PotionData potionData = new PotionData(PotionType.WATER);
				potionMeta.setBasePotionData(potionData);
				itemStack.setItemMeta(itemMeta);
				changeItemFromPlayer(paramPlayer, new ItemStack(itemStack));
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

	private void showFountainParticles(Location location, double locationYOffset, int count, double offsetY) {
		Location locationClone = location.clone().add(0.5D,locationYOffset, 0.5D);
		for (byte b = 0; b < 4; b++)
			Bukkit.getScheduler().runTaskLater(LiquidTanks.instance, () -> locationClone.getWorld().spawnParticle(SPLASH, locationClone, count * 10, 0.05F, offsetY, 0.05F, 0.01D),(b * 5));
	}
}
