package nl.hauntedmc.serverfeatures.features.enderframe;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.enderframe.listener.BlockBreakListener;
import nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class EnderFrame extends BaseFeature<Meta> {

    private boolean griefPreventionEnabled;

    public EnderFrame(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Provide default settings for this feature.
     */
    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("require_permission", true);
        defaults.put("pickup_radius", 5);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("enderframe.pickup_success", "&aJe hebt een Ender Frame opgepakt!");
        messageMap.add("enderframe.claim_restricted", "&cJe kunt de Ender Frame niet oppakken in andermans claim.");
        return messageMap;
    }

    /**
     * Called when the feature is enabled. Register your listener(s) here.
     */
    @Override
    public void initialize() {
        griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        getLifecycleManager().registerListener(new BlockBreakListener(this));
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

}
