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
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                gameTick(feature);
            } catch (Exception ignored) {
            }
        }, BukkitTime.ticks(delay), BukkitTime.ticks(delay));
    }

    private static void gameTick(LiquidTank feature) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            final GameMode gm = player.getGameMode();
            final boolean canPlay = (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
            if (!canPlay) continue;

            // === WITHDRAW: hopper ~2.75 blocks above the player ===
            // Use integer 3 blocks for the block check (same as original intent for a block scan).
            Block above = player.getLocation().add(0, 3, 0).getBlock();
            if (above.getType() == Material.HOPPER) {
                AbstractTank tank = feature.getTankManager().getTank(above.getLocation());
                if (tank instanceof ExperienceTank) {
                    int qty = tank.getQuantity();
                    if (qty > 0) {
                        int transfer = Math.min(100, qty); // up to 100 xp
                        ExperienceUtil.addExp(player, transfer);
                        qty -= transfer;

                        if (qty <= 0) {
                            // Emptied: convert to EMPTY and show effects (match original behavior)
                            AbstractTank emptied = feature.getTankManager().emptyTank(tank);
                            emptied.updateVisuals();
                            tank.showParticles();
                        } else {
                            tank.setQuantity(qty);
                            tank.updateVisuals();
                            tank.showParticles();
                        }
                    }
                }
            }

            // === DEPOSIT: sneaking with hopper slightly below the player ===
            if (player.isSneaking()) {
                int total = ExperienceUtil.totalExp(player);
                if (total > 0) {
                    Block below = player.getLocation().add(0, -1, 0).getBlock(); // ~-0.1D → block directly below
                    if (below.getType() == Material.HOPPER) {
                        AbstractTank tank = feature.getTankManager().getTank(below.getLocation());
                        if (tank != null) {
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
                                newTank.updateVisuals();
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
