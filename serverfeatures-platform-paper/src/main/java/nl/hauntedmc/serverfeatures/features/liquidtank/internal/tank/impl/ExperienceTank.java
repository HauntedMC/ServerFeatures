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

import java.util.logging.Level;

import static org.bukkit.Material.LIME_WOOL;
import static org.bukkit.Material.YELLOW_WOOL;

public final class ExperienceTank extends AbstractTank {
    private static final TankType type = TankType.EXPERIENCE;

    private static final long delay = 20L;

    private static final int maxAmount = 1395;

    public ExperienceTank(Location location, int amount, LiquidTank feature) {
        super(location, amount, feature);
        updateVisuals();
    }

    public static TankType getType() {
        return type;
    }

    public static void startGameLoop(LiquidTank feature) {
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                gameTick(feature);
            } catch (RuntimeException exception) {
                feature.getLogger().log(
                        Level.WARNING,
                        "Liquid tank experience transfer tick failed.",
                        exception
                );
            }
        }, BukkitTime.ticks(delay), BukkitTime.ticks(delay));
    }

    private static void gameTick(LiquidTank feature) {
        if (!feature.getTankManager().hasExperienceTanks()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            final GameMode gm = player.getGameMode();
            final boolean canPlay = (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
            if (!canPlay) continue;
            Location playerLocation = player.getLocation();

            Block above = player.getWorld().getBlockAt(
                    playerLocation.getBlockX(),
                    playerLocation.getBlockY() + 3,
                    playerLocation.getBlockZ()
            );
            if (above.getType() == Material.HOPPER) {
                AbstractTank tank = feature.getTankManager().getTank(above);
                if (tank instanceof ExperienceTank) {
                    int qty = tank.getQuantity();
                    if (qty > 0) {
                        int transfer = Math.min(100, qty); // up to 100 xp
                        ExperienceUtil.addExp(player, transfer);
                        qty -= transfer;

                        if (qty <= 0) {
                            // Emptied: convert to EMPTY and show effects (match original behavior)
                            feature.getTankManager().emptyTank(tank);
                            tank.showParticles();
                        } else {
                            tank.setQuantity(qty);
                            tank.updateVisuals();
                            tank.showParticles();
                        }
                    }
                }
            }

            if (player.isSneaking()) {
                Block below = player.getWorld().getBlockAt(
                        playerLocation.getBlockX(),
                        playerLocation.getBlockY() - 1,
                        playerLocation.getBlockZ()
                );
                if (below.getType() == Material.HOPPER) {
                    AbstractTank tank = feature.getTankManager().getTank(below);
                    if (tank != null) {
                        int total = ExperienceUtil.totalExp(player);
                        if (total > 0) {
                            if (tank instanceof ExperienceTank) {
                                int qty = tank.getQuantity();
                                int cap = tank.getMaxQuantity() - qty;
                                if (cap > 0) {
                                    int deposit = Math.min(100, Math.min(total, cap));
                                    ExperienceUtil.removeExp(player, deposit);
                                    tank.setQuantity(qty + deposit);
                                    tank.updateVisuals();
                                    tank.playTitle(player);
                                }
                            } else if (tank instanceof EmptyTank) {
                                int deposit = Math.min(100, total);
                                ExperienceUtil.removeExp(player, deposit);
                                AbstractTank newTank = feature.getTankManager()
                                        .changeTankType(tank, TankType.EXPERIENCE, deposit);
                                player.updateInventory(); // kept from your original
                                newTank.playTitle(player);
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
            if (getQuantity() + 7 <= getMaxQuantity()) {
                changeItemFromPlayer(paramPlayer, new ItemStack(Material.GLASS_BOTTLE));
                setQuantity(getQuantity() + 7);
                updateVisuals();
            }
        } else if (paramPlayer.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
            int remaining = remainingAfterBottleWithdrawal(getQuantity());
            if (remaining < 0) {
                playTitle(paramPlayer);
                return;
            }
            changeItemFromPlayer(paramPlayer, new ItemStack(Material.EXPERIENCE_BOTTLE));
            if (remaining == 0) {
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
                abstractTank.playTitle(paramPlayer);
                return;
            }
            setQuantity(remaining);
            updateVisuals();
        }
        playTitle(paramPlayer);
    }

    static int remainingAfterBottleWithdrawal(int quantity) {
        return quantity < 7 ? -1 : quantity - 7;
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
        MessageUtils.sendActionbar(paramPlayer, stringBuilder.toString());
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
