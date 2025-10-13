package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.BlockUtils;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.util.HeadURL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class LavaTank extends AbstractTank {
    private static final TankType type = TankType.LAVA;

    private static final int maxAmount = 128;

    private static final long delay = 100L;

    public LavaTank(Location location, int amount, LiquidTank feature) {
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
        for (AbstractTank abstractTank : feature.getTankManager().getTankList()) {
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
                        if (block.getState() instanceof BlastFurnace blastFurnace) {
                            ItemStack itemStack = null;
                            try {
                                itemStack = blastFurnace.getInventory().getItem(0);
                            } catch (Exception ignored) {
                            }
                            if (itemStack != null && blastFurnace.getBurnTime() == 0) {
                                blastFurnace.setBurnTime((short) 6667);
                                blastFurnace.update();
                                if (abstractTank.getQuantity() > 1) {
                                    abstractTank.setQuantity(abstractTank.getQuantity() - 1);
                                    abstractTank.updateVisuals();
                                } else if (abstractTank.getQuantity() == 1) {
                                    feature.getTankManager().emptyTank(abstractTank);
                                    break;
                                }
                            }
                        } else if (block.getState() instanceof Smoker smoker) {
                            ItemStack itemStack = null;
                            try {
                                itemStack = smoker.getInventory().getItem(0);
                            } catch (Exception ignored) {
                            }
                            if (itemStack != null && smoker.getBurnTime() == 0) {
                                smoker.setBurnTime((short) 6667);
                                smoker.update();
                                if (abstractTank.getQuantity() > 1) {
                                    abstractTank.setQuantity(abstractTank.getQuantity() - 1);
                                    abstractTank.updateVisuals();
                                } else if (abstractTank.getQuantity() == 1) {
                                    feature.getTankManager().emptyTank(abstractTank);
                                    break;
                                }
                            }
                        }
                        if (block.getType() == Material.FURNACE) {
                            Furnace furnace = (Furnace) block.getState();
                            ItemStack itemStack = null;
                            try {
                                itemStack = furnace.getInventory().getItem(0);
                            } catch (Exception ignored) {
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
                                    feature.getTankManager().emptyTank(abstractTank);
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
                            feature.getTankManager().changeTankType(abstractTank, TankType.LAVA, 3);
                            continue;
                        }
                        abstractTank.setQuantity(Math.min(abstractTank.getQuantity() + 3, abstractTank.getMaxQuantity()));
                        abstractTank.updateVisuals();
                    }
                }
            }
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
                AbstractTank abstractTank = feature.getTankManager().emptyTank(this);
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
    public String getChatColor() {
        return "&c";
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
