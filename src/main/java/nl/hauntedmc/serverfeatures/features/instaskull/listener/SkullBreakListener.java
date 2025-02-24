package nl.hauntedmc.serverfeatures.features.instaskull.listener;

import nl.hauntedmc.serverfeatures.features.instaskull.InstaSkull;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

public class SkullBreakListener implements Listener {

    private final InstaSkull feature;

    public SkullBreakListener(InstaSkull feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.instaskull.use")) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD) {
            event.setInstaBreak(true);
        }
    }
}