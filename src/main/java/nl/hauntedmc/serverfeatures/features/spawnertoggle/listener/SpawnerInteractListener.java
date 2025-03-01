package nl.hauntedmc.serverfeatures.features.spawnertoggle.listener;


import nl.hauntedmc.serverfeatures.features.spawnertoggle.SpawnerToggle;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class SpawnerInteractListener implements Listener {

    private final SpawnerToggle feature;

    public SpawnerInteractListener(SpawnerToggle feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() == EquipmentSlot.HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || clickedBlock.getType() != Material.SPAWNER) return;

            if (feature.isGriefPreventionEnabled()) {
                if (!feature.checkBuildPermissions(player, clickedBlock.getLocation())) {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("spawner_toggle.claim_restricted", player));
                    return;
                }
            }

            feature.toggleSpawner(player, clickedBlock);
        }
    }
}