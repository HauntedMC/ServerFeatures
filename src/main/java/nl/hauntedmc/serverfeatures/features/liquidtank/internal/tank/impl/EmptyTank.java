package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet.PacketHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.PotionUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EmptyTank extends AbstractTank {
	private static final ChatColor chatColor = ChatColor.GRAY;

    private static final long delay = 100L;

	public EmptyTank(Location location, LiquidTank feature) {
		super(location, 0, feature);
	}

	public static void gameLoop(LiquidTank feature) {
	}

	private static void gameTick() {
		//
	}

	@Override
	public void updateVisuals() {
		clear(false);
		Location location = new Location(getLocation().getWorld(), getLocation().getX(), getLocation().getY(), getLocation().getZ());
		setPacketArmorstandGlass(new PacketHandler(location.clone().add(0.5D, 0.35D, 0.5D)));
		getPacketArmorstandGlass().setHead(ItemCreator.newItem(Material.GLASS, 1, "", ""));
		updatePlayerView();
	}

	@Override
	protected void updateLiquidLevel() {
		// skip this for empty tank.
	}

	@Override
	protected String getLiquidHeadUrl() {
		return null;
	}

	@Override
	public void onInteract(Player paramPlayer) {
		if (paramPlayer.getInventory().getItemInMainHand().getType() != Material.AIR) {
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.LAVA_BUCKET) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.LAVA, 3);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.WATER_BUCKET) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.WATER, 3);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (PotionUtils.isWaterBottle(paramPlayer.getInventory().getItemInMainHand())) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.WATER, 1);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.MILK_BUCKET) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BUCKET));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.MILK, 3);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.MUSHROOM_STEW) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.MUSHROOM_STEW, 1);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.RABBIT_STEW) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.RABBIT_STEW, 1);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.EXPERIENCE_BOTTLE) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.EXPERIENCE, 7);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.BEETROOT_SOUP) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.BOWL));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.BEETROOT_SOUP, 1);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.DRAGON_BREATH) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.DRAGON_BREATH, 1);
				abstractTank.playTitle(paramPlayer);
				return;
			}
			if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.HONEY_BOTTLE) {
				changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
				AbstractTank abstractTank = feature.getTankManager().changeTankType(this, TankType.HONEY, 1);
				abstractTank.playTitle(paramPlayer);
				return;
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
		return TankType.EMPTY;
	}

	@Override
	public int getMaxQuantity() {
        return 128;
	}

	@Override
	protected void showParticles() {
		// this tank doesn't have particles
	}
}
