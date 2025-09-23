package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet.PacketHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.ItemCreator;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.bukkit.Particle.FALLING_DUST;

public abstract class AbstractTank {
	private static final String chatColor = "&8";

	private static final int maxAmount = 128;

	private static final int cooldownTime = 50;

	private static final long delay = 100L;

	private int amount;

	private Location location;

	private PacketHandler packetHandlerGlass = null;

	private PacketHandler packetHandlerLiquid = null;

	private final List<String> playersNearby = new ArrayList<>();

	private boolean onCooldown = false;
	protected final LiquidTank feature;

	public AbstractTank(Location location, int amount, LiquidTank feature) {
		this.feature = feature;
		this.amount = amount;
		this.location = location;
		this.location.setYaw(0.0F);
		this.location.setPitch(0.0F);
		updateVisuals();
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public PacketHandler getPacketArmorstandGlass() {
		return packetHandlerGlass;
	}

	public void setPacketArmorstandGlass(PacketHandler packetHandlerGlass) {
		this.packetHandlerGlass = packetHandlerGlass;
	}

	public PacketHandler getPacketArmorstandLiquid() {
		return packetHandlerLiquid;
	}

	public void setPacketArmorstandLiquid(PacketHandler packetHandlerLiquid) {
		this.packetHandlerLiquid = packetHandlerLiquid;
	}

	public static void gameLoop(LiquidTank feature) {
		feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
			try {
				gameTick(feature);
			} catch (Exception ignored) {
			}
		}, BukkitTime.ticks(delay), BukkitTime.ticks(delay));
	}

	private static void gameTick(LiquidTank feature) {
		ArrayList<AbstractTank> arrayList = new ArrayList<>();
		for (AbstractTank abstractTank : feature.getTankManager().getTankList()) {
			if (BlockUtils.isLoaded(abstractTank.getLocation()) && abstractTank.getLocation().getBlock().getType() != Material.HOPPER)
				arrayList.add(abstractTank);
		}
		for (AbstractTank abstractTank : arrayList)
			feature.getTankManager().removeTank(abstractTank);
	}

	public void onInteract(Player paramPlayer) {
	}

	public void setOnCooldown() {
		this.onCooldown = true;
		feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() ->  this.onCooldown = false, BukkitTime.ticks(cooldownTime));
	}

	public boolean isOnCooldown() {
		return this.onCooldown;
	}

	public boolean isOverFlown() {
		return (getQuantity() > getMaxQuantity());
	}

	public void playTitle(Player paramPlayer) {
		StringBuilder stringBuilder = new StringBuilder("&7[");
		stringBuilder.append(getChatColor()).append("&l");
		int i = getMaxQuantity() / 41 + 1;
		int j = getMaxQuantity() / i;
		if (j % 2 == 1)
			j++;
		double d = (double) getQuantity() / getMaxQuantity();
		int k = (int) (d * j);
		String str = "&7";
		if (isOverFlown())
			str = "&c";
		for (byte b = 0; b < j; b++) {
			if (b == j / 2 && k <= j / 2)
				stringBuilder.append(str).append(" ").append("&l")
						.append(getQuantity()).append(" &8").append("&l");
			if (b == j / 2 && k > j / 2)
				stringBuilder.append(str).append(" ").append("&l")
						.append(getQuantity()).append(" ").append(getChatColor())
						.append("&l");
			if (b < k)
				stringBuilder.append("|");
			if (b == k)
				stringBuilder.append("&8").append("&l");
			if (b >= k)
				stringBuilder.append("|");
		}
		stringBuilder.append("&7]");
		MessageUtils.sendTitle(paramPlayer, stringBuilder.toString());
	}

	public void clear() {
		this.playersNearby.clear();
		if (this.packetHandlerGlass != null)
			for (Player player : Bukkit.getOnlinePlayers())
				this.packetHandlerGlass.hide(player);
		if (this.packetHandlerLiquid != null)
			for (Player player : Bukkit.getOnlinePlayers())
				this.packetHandlerLiquid.hide(player);
	}

	public void updateVisuals() {
		clear();
		this.packetHandlerGlass = new PacketHandler(getLocation().clone().add(0.5D, 0.4D, 0.5D));
		this.packetHandlerGlass.setHead(ItemCreator.newItem(Material.GLASS, 1, "", ""));
		updateLiquidLevel();
		updatePlayerView();
	}

	protected void updateLiquidLevel() {
		double d1 = -0.025D;
		double d2 = 0.35D;
		int i = getAmount();
		if (getAmount() > maxAmount)
			i = maxAmount;
		double d3 = (d2 - d1) * i / maxAmount;
		this.packetHandlerLiquid = new PacketHandler(getLocation().clone().add(0.5D, 0.35D - d2 + d3, 0.5D));
		this.packetHandlerLiquid.setHead(HeadURL.create(getLiquidHeadUrl()));
	}

	protected abstract String getLiquidHeadUrl();
	
	public void updatePlayerView() {
		this.playersNearby.clear();
		if (BlockUtils.isLoaded(this.location))
			for (Player player : Bukkit.getOnlinePlayers())
				updatePlayerView(player);
	}

	public void updatePlayerView(Player paramPlayer) {
		if (BlockUtils.isLoaded(this.location))
			if (!this.playersNearby.contains(paramPlayer.getName())) {
				if (paramPlayer.getWorld() == this.location.getWorld() && paramPlayer
						.getLocation().distance(this.location) <= 20.0D) {
					if (this.packetHandlerGlass != null)
						this.packetHandlerGlass.show(paramPlayer);
					if (this.packetHandlerLiquid != null)
						this.packetHandlerLiquid.show(paramPlayer);
					this.playersNearby.add(paramPlayer.getName());
				}
			} else if (paramPlayer.getWorld() == this.location.getWorld() && paramPlayer
					.getLocation().distance(this.location) > 20.0D) {
				if (this.packetHandlerGlass != null)
					this.packetHandlerGlass.hide(paramPlayer);
				if (this.packetHandlerLiquid != null)
					this.packetHandlerLiquid.hide(paramPlayer);
				this.playersNearby.remove(paramPlayer.getName());
			}
	}

	public String getChatColor() {
		return chatColor;
	}

	public TankType getTankType() {
		return TankType.EMPTY;
	}

	public int getMaxQuantity() {
		return maxAmount;
	}

	public int getQuantity() {
		return this.amount;
	}

	public void setQuantity(int paramInt) {
		this.amount = paramInt;
	}

	public Location getLocation() {
		return this.location;
	}

	public void changeItemFromPlayer(Player paramPlayer, ItemStack paramItemStack) {
		if (!paramPlayer.getGameMode().equals(GameMode.CREATIVE))
			if (paramPlayer.getInventory().getItemInMainHand().getAmount() > 1) {
				paramPlayer.getInventory().getItemInMainHand()
						.setAmount(paramPlayer.getInventory().getItemInMainHand().getAmount() - 1);
				HashMap<Integer, ItemStack> hashMap = paramPlayer.getInventory().addItem(paramItemStack);
				if (!hashMap.isEmpty())
					for (ItemStack itemStack : hashMap.values())
						feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> paramPlayer.getWorld().dropItem(paramPlayer.getLocation(), paramItemStack));
			} else {
				paramPlayer.getInventory().setItemInMainHand(paramItemStack);
			}
	}

	public static void spawnFallingDust(Location location, int count, float offset, float offsetY, Material material) {
		location.getWorld().spawnParticle(FALLING_DUST, location, count, offset * 3.0F, offsetY, offset * 3.0F, material.createBlockData());
	}

	protected abstract void showParticles();
	
}
