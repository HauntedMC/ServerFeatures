package nl.hauntedmc.serverfeatures.features.villageroptimizer.listener;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.VillagerOptimizer;
import nl.hauntedmc.serverfeatures.features.villageroptimizer.internal.VillagerAIHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;

public class VillagerEventListener implements Listener {

    private final VillagerAIHandler villagerAIHandler;
    public VillagerOptimizer feature;

    public VillagerEventListener(VillagerOptimizer feature) {
        this.feature = feature;
        this.villagerAIHandler = feature.getVillagerAIHandler();
    }

    @EventHandler
    public void onCancelVillagerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager vil && event.getDamager() instanceof Zombie)) return;

        if (villagerAIHandler.getDisabledByBlock(vil)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void inventoryMove(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Villager vil)) return;

        if (villagerAIHandler.getDisabledByBlock(vil)) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.villagerMustBeDisabled").forAudience(player).build());
    }

    @EventHandler
    public void villagerTradeClick(TradeSelectEvent event) {
        if (!(event.getInventory().getHolder() instanceof Villager vil)) return;

        if (villagerAIHandler.getDisabledByBlock(vil)) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.villagerMustBeDisabled").forAudience(player).build());

    }

    @EventHandler
    public void rightClick(PlayerInteractEntityEvent e) {
        Player player = e.getPlayer();

        if (!e.getRightClicked().getType().equals(EntityType.VILLAGER)) return;


        boolean canBuild = true;
        if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            canBuild = checkBuildPermissions(player);
        }
        if (canBuild) {
            Villager vil = (Villager) e.getRightClicked();
            // check whether this villager has cooldown tags
            if (!villagerAIHandler.hasCooldown(vil)) villagerAIHandler.setNewCooldown(vil, (long) 0);
            if (!villagerAIHandler.hasLevelCooldown(vil))
                villagerAIHandler.setLevelCooldown(vil, (long) 0);
            if (!villagerAIHandler.hasTime(vil)) villagerAIHandler.setNewTime(vil);

            long currentTime = System.currentTimeMillis() / 1000;

            // if time is broken fix it!
            villagerAIHandler.sanityChecks(vil, currentTime);

            long vilLevelCooldown = villagerAIHandler.getLevelCooldown(vil);

            long totalSeconds = vilLevelCooldown - currentTime;
            long sec = totalSeconds % 60;

            // Check if the villager is disabled for leveling and send a message
            if (villagerAIHandler.getDisabledByBlock(vil)) {
                if (vilLevelCooldown > currentTime) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("villageroptimizer.cooldownLevelupMessage")
                            .forAudience(player)
                            .withPlaceholders(Map.of("time_sec",  Long.toString(sec)))
                            .build());
                    vil.shakeHead();
                    e.setCancelled(true);
                    return;
                }
            }
            // handle Block AI
            if (!e.isCancelled()) {
                if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.EMERALD_BLOCK) {
                    villagerAIHandler.toggleVillagerAI(vil, player);
                    e.setCancelled(true);
                }
            }
            // handle Restock, check if Villager is disabled before
            if (villagerAIHandler.getDisabledByBlock(vil)) {
                villagerAIHandler.getVillagerRestockHandler().restockVillager(vil, player);
            }
            // check what this does. If villager is disabled, and doesnt have AI, enable AI?
            if (villagerAIHandler.getDisabledByBlock(vil))
                if (!vil.hasAI()) {
                    vil.setAI(true);
                    vil.setAware(false);
                }
        }
    }

    @EventHandler
    public void afterTrade(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // check if inventory belongs to a Villager Trade Screen
        if (event.getInventory().getHolder() instanceof WanderingTrader) return;

        if (event.getInventory().getType() != InventoryType.MERCHANT) return;

        if (event.getInventory().getHolder() == null) return;

        Villager vil = (Villager) event.getInventory().getHolder();

        if (!villagerAIHandler.hasDisabledByBlock(vil)) return;
        if (!villagerAIHandler.hasLevelCooldown(vil)) villagerAIHandler.setLevelCooldown(vil, (long) 0);

        villagerAIHandler.getVillagerLevelHandler().updateVillagerLevel(vil, player);
    }

    private boolean checkBuildPermissions(Player player) {
        Location currentloc = player.getLocation();
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(currentloc, false, null);

        if (claim != null) {
            return claim.allowBreak(player, Material.SPAWNER) == null;
        } else {
            return true;
        }
    }
}
