package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

public class SpawnerToggle extends BaseFeature<Meta> {

    private boolean griefPreventionEnabled;

    public SpawnerToggle(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("default_spawn_range", 16); // Default Minecraft setting
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("spawner_toggle.toggle_message", "&7[&bSpawner&7] Mob spawning is {status} &7voor deze spawner.");
        messageMap.add("spawner_toggle.claim_restricted", "&cJe kunt deze spawner niet bewerken in andermans claim.");
        return messageMap;
    }

    @Override
    public void initialize() {
        getLifecycleManager().registerListener(new SpawnerInteractListener());
        griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
    }

    private class SpawnerInteractListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onSpawnerInteract(PlayerInteractEvent event) {
            Player player = event.getPlayer();

            if (event.getHand() == EquipmentSlot.HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null || clickedBlock.getType() != Material.SPAWNER) return;

                if (griefPreventionEnabled) {
                    if (!checkBuildPermissions(player, clickedBlock.getLocation())) {
                        player.sendMessage(getLocalizationHandler().getMessage("spawner_toggle.claim_restricted"));
                        return;
                    }
                }

                toggleSpawner(player, clickedBlock);
            }
        }

        private void toggleSpawner(Player player, Block block) {
            BlockState blockState = block.getState();
            if (!(blockState instanceof CreatureSpawner spawner)) return;

            int defaultRange = (int) getConfigHandler().getSetting("default_spawn_range");

            if (spawner.getRequiredPlayerRange() == defaultRange) {
                spawner.setRequiredPlayerRange(0);
                player.sendMessage(getLocalizationHandler().getMessage("spawner_toggle.toggle_message", Map.of("status", "&aingeschakeld")));
            } else {
                spawner.setRequiredPlayerRange(defaultRange);
                player.sendMessage(getLocalizationHandler().getMessage("spawner_toggle.toggle_message", Map.of("status", "&cuitgeschakeld")));
            }

            blockState.update();
        }
        private boolean checkBuildPermissions(Player player, Location location) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            return claim == null || claim.allowBreak(player, Material.SPAWNER) == null;
        }
    }
}
