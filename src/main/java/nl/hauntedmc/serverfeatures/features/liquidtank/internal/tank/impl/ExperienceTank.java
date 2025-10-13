package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet.PacketHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ExperienceUtil;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.LIME_WOOL;
import static org.bukkit.Material.YELLOW_WOOL;

public class ExperienceTank extends AbstractTank {
	private static final TankType type = TankType.EXPERIENCE;

	private static final long delay = 20L;

	private static final int maxAmount = 1395;

	public ExperienceTank(Location location, int amount, LiquidTank feature) {
		super(location, amount, feature);
	}

	public static TankType getType() {
		return type;
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
		for (Player player : Bukkit.getOnlinePlayers()) {
			Block block = player.getLocation().add(0.0D, 2.75D, 0.0D).getBlock();
			if (block.getType() == Material.HOPPER) {
				AbstractTank abstractTank = feature.getTankManager().getTank(block.getLocation());
				if (abstractTank != null && (player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode().equals(GameMode.ADVENTURE)) && abstractTank instanceof ExperienceTank)
					if (abstractTank.getQuantity() > 100) {
						ExperienceUtil.addExp(player, 100);
						abstractTank.setQuantity(abstractTank.getQuantity() - 100);
						abstractTank.updateVisuals();
						abstractTank.showParticles();
					} else if (abstractTank.getQuantity() <= 100) {
						ExperienceUtil.addExp(player, abstractTank.getQuantity());
						AbstractTank abstractTank1 = feature.getTankManager().emptyTank(abstractTank);
						abstractTank1.updateVisuals();
						abstractTank.showParticles();
					}
			}
			if (player.isSneaking()) {
				int i = ExperienceUtil.totalExp(player);
				if (i != 0 && (player.getGameMode().equals(GameMode.SURVIVAL) || player.getGameMode()
						.equals(GameMode.ADVENTURE))) {
					block = player.getLocation().add(0.0D, -0.1D, 0.0D).getBlock();
					if (block.getType() == Material.HOPPER) {
						AbstractTank abstractTank = feature.getTankManager().getTank(block.getLocation());
						if (abstractTank != null) {
							if (abstractTank instanceof ExperienceTank && abstractTank.getQuantity() < abstractTank
									.getMaxQuantity()) {
								if (abstractTank.getQuantity() + 100 <= abstractTank.getMaxQuantity() && i >= 100) {
									ExperienceUtil.removeExp(player, 100);
									abstractTank.setQuantity(abstractTank.getQuantity() + 100);
									abstractTank.updateVisuals();
									abstractTank.playTitle(player);
									continue;
								}
								if (i < 100 && abstractTank
										.getMaxQuantity() - abstractTank.getQuantity() <= i) {
									ExperienceUtil.removeExp(player, abstractTank.getMaxQuantity() - abstractTank.getQuantity());
									abstractTank.setQuantity(abstractTank.getMaxQuantity());
									abstractTank.updateVisuals();
									abstractTank.playTitle(player);
									continue;
								}
								if (i >= 100 && abstractTank
										.getMaxQuantity() - abstractTank.getQuantity() <= 100) {
									ExperienceUtil.removeExp(player, abstractTank.getMaxQuantity() - abstractTank.getQuantity());
									abstractTank.setQuantity(abstractTank.getMaxQuantity());
									abstractTank.updateVisuals();
									abstractTank.playTitle(player);
									continue;
								}
								if (i < 100 && abstractTank
										.getMaxQuantity() - abstractTank.getQuantity() > i) {
									ExperienceUtil.removeExp(player, i);
									abstractTank.setQuantity(abstractTank.getQuantity() + i);
									abstractTank.updateVisuals();
									abstractTank.playTitle(player);
								}
								continue;
							}
							if (abstractTank instanceof EmptyTank) {
								if (i >= 100) {
									ExperienceUtil.removeExp(player, 100);
									abstractTank = feature.getTankManager().changeTankType(abstractTank, TankType.EXPERIENCE, 100);
									abstractTank.updateVisuals();
									abstractTank.playTitle(player);
									continue;
								}
								ExperienceUtil.removeExp(player, i);
								abstractTank = feature.getTankManager().changeTankType(abstractTank, TankType.EXPERIENCE, i);
								abstractTank.updateVisuals();
								player.updateInventory();
								abstractTank.playTitle(player);
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected void updateLiquidLevel() {
		double d1 = -0.025D;
		double d2 = 0.35D;
		double d3 = (d2 - d1) * getAmount() / maxAmount;
		setPacketArmorstandLiquid(new PacketHandler(getLocation().clone().add(0.5D, 0.35D - d2 + d3, 0.5D)));
		getPacketArmorstandLiquid().setHead(HeadURL.create(getLiquidHeadUrl()));
	}

	@Override
	protected String getLiquidHeadUrl() {
		return HeadURL.experienceB64;
	}

	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.EXPERIENCE_BOTTLE) {
			if (getQuantity() + 1 <= getMaxQuantity()) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
				setQuantity(getQuantity() + 7);
				updateVisuals();
			}
		} else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
			if (getQuantity() < 14) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.EXPERIENCE_BOTTLE));
				AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
				abstractTank.playTitle(paramPlayer);
				abstractTank.updateVisuals();
				return;
			}
			if (getQuantity() > 7) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.EXPERIENCE_BOTTLE));
				setQuantity(getQuantity() - 7);
				updateVisuals();
			}
		}
		playTitle(paramPlayer);
	}

	@Override
	public void playTitle(Player paramPlayer) {
		StringBuilder stringBuilder = new StringBuilder("&7[");
		stringBuilder.append(getChatColor()).append("&l");
		int i = ExperienceUtil.getLevel(getMaxQuantity());
		int j = ExperienceUtil.getLevel(getQuantity());
		double d = (i / 41 + 1);
		for (byte b = 0; b < i / d; b++) {
			if (b == i / d / 2.0D && j / d <= i / d / 2.0D)
				stringBuilder.append("&7 Lvl. ").append("&l").append(j).append(" &8")
						.append("&l");
			if (b == i / d / 2.0D && j / d > i / d / 2.0D)
				stringBuilder.append("&7 Lvl. ").append("&l").append(j).append(" ")
						.append(getChatColor()).append("&l");
			if (b < j / d)
				stringBuilder.append("|");
			if (b == j / d)
				stringBuilder.append("&8").append("&l");
			if (b >= j / d)
				stringBuilder.append("|");
		}
		stringBuilder.append("&7]");
		MessageUtils.sendTitle(paramPlayer, stringBuilder.toString());
	}

	@Override
	public String getChatColor() {
		return "&a";
	}

	@Override
	public TankType getTankType() {
		return TankType.EXPERIENCE;
	}

	@Override
	public int getMaxQuantity() {
		return maxAmount;
	}

	@Override
	protected void showParticles() {
		Location location = getLocation().clone().add(0.5D, 0.0D, 0.5D);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, LIME_WOOL);
		AbstractTank.spawnFallingDust(location, 10, 0.05F, 0.1F, YELLOW_WOOL);
	}
}
