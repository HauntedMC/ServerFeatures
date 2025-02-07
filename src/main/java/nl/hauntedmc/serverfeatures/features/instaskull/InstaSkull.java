package nl.hauntedmc.serverfeatures.features.instaskull;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.HashMap;
import java.util.Map;

public class InstaSkull extends BaseFeature<Meta> {

    public InstaSkull(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("require_permission", true);
        return defaults;
    }

    @Override
    public void initialize() {
        getLifecycleManager().registerListener(new SkullDamageListener());
    }

    private class SkullDamageListener implements Listener {
        @EventHandler
        public void onBlockDamage(BlockDamageEvent event) {
            Player player = event.getPlayer();

            boolean requiresPermission = getConfigHandler().getBoolean("require_permission", true);
            if (requiresPermission && !player.hasPermission("serverfeatures.feature.instaskull.use")) {
                return;
            }

            Block block = event.getBlock();
            if (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD) {
                event.setInstaBreak(true);
            }
        }
    }
}
