package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet.PacketHandler;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.bukkit.Particle.FALLING_DUST;

public abstract class AbstractTank {
    private static final String chatColor = "&8";

    private static final int maxAmount = 128;

    private static final int cooldownTime = 50;
    private static final double VIEW_DISTANCE_SQUARED = 20.0D * 20.0D;

    private int amount;

    private final Location location;

    private PacketHandler packetHandlerGlass = null;

    private PacketHandler packetHandlerLiquid = null;

    private int visualizedAmount = Integer.MIN_VALUE;

    private final Set<UUID> viewers = new HashSet<>();

    private int cooldownUntilTick;
    protected final LiquidTank feature;

    public AbstractTank(Location location, int amount, LiquidTank feature) {
        this.feature = feature;
        this.amount = amount;
        this.location = location.clone();
        this.location.setYaw(0.0F);
        this.location.setPitch(0.0F);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        if (this.amount == amount) {
            return;
        }
        this.amount = amount;
        feature.getTankManager().markDirty();
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

    public void onInteract(Player paramPlayer) {
    }

    public void setOnCooldown() {
        cooldownUntilTick = Bukkit.getCurrentTick() + cooldownTime;
    }

    public boolean isOnCooldown() {
        return cooldownUntilTick - Bukkit.getCurrentTick() > 0;
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
        MessageUtils.sendActionbar(paramPlayer, stringBuilder.toString());
    }

    public void clear() {
        List<Player> currentViewers = onlineViewers();
        hideVisuals(currentViewers);
        viewers.clear();
        packetHandlerGlass = null;
        packetHandlerLiquid = null;
        visualizedAmount = Integer.MIN_VALUE;
    }

    public void updateVisuals() {
        List<Player> currentViewers = onlineViewers();
        boolean glassCreated = packetHandlerGlass == null;
        if (glassCreated) {
            packetHandlerGlass = new PacketHandler(
                    getLocation().clone().add(0.5D, 0.4D, 0.5D)
            );
            packetHandlerGlass.setHead(new ItemStack(Material.GLASS));
        }
        if (packetHandlerLiquid != null && visualizedAmount == amount) {
            if (glassCreated) {
                for (Player viewer : currentViewers) {
                    packetHandlerGlass.show(viewer);
                }
            }
            return;
        }
        if (packetHandlerLiquid != null) {
            hideHandler(packetHandlerLiquid, currentViewers);
        }
        packetHandlerLiquid = null;
        updateLiquidLevel();
        visualizedAmount = amount;
        for (Player viewer : currentViewers) {
            if (glassCreated) {
                packetHandlerGlass.show(viewer);
            }
            if (packetHandlerLiquid != null) {
                packetHandlerLiquid.show(viewer);
            }
        }
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

    public boolean isVisibleFrom(Location playerLocation) {
        if (!BlockUtils.isLoaded(location)
                || playerLocation.getWorld() != location.getWorld()) {
            return false;
        }
        double x = playerLocation.getX() - location.getX();
        double y = playerLocation.getY() - location.getY();
        double z = playerLocation.getZ() - location.getZ();
        return x * x + y * y + z * z <= VIEW_DISTANCE_SQUARED;
    }

    public void showTo(Player player) {
        if (!viewers.add(player.getUniqueId())) {
            return;
        }
        showVisuals(player);
    }

    public void hideFrom(Player player) {
        if (!viewers.remove(player.getUniqueId())) {
            return;
        }
        hideVisuals(player);
    }

    public void forgetViewer(UUID playerId) {
        viewers.remove(playerId);
    }

    public Set<UUID> viewerIds() {
        return Set.copyOf(viewers);
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
        setAmount(paramInt);
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
                if (!hashMap.isEmpty()) {
                    for (ItemStack itemStack : hashMap.values()) {
                        paramPlayer.getWorld().dropItem(paramPlayer.getLocation(), itemStack);
                    }
                }
            } else {
                paramPlayer.getInventory().setItemInMainHand(paramItemStack);
            }
    }

    public static void spawnFallingDust(Location location, int count, float offset, float offsetY, Material material) {
        location.getWorld().spawnParticle(FALLING_DUST, location, count, offset * 3.0F, offsetY, offset * 3.0F, material.createBlockData());
    }

    protected abstract void showParticles();

    protected final List<Player> onlineViewers() {
        return viewers.stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(Player::isOnline)
                .toList();
    }

    private void showVisuals(Player player) {
        if (packetHandlerGlass != null) {
            packetHandlerGlass.show(player);
        }
        if (packetHandlerLiquid != null) {
            packetHandlerLiquid.show(player);
        }
    }

    private void hideVisuals(List<Player> players) {
        for (Player player : players) {
            hideVisuals(player);
        }
    }

    protected final void hideHandler(PacketHandler handler, List<Player> players) {
        for (Player player : players) {
            handler.hide(player);
        }
    }

    private void hideVisuals(Player player) {
        if (packetHandlerGlass != null) {
            packetHandlerGlass.hide(player);
        }
        if (packetHandlerLiquid != null) {
            packetHandlerLiquid.hide(player);
        }
    }

}
