package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.listener.SpawnerInteractListener;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;

public class SpawnerToggle extends BukkitBaseFeature<Meta> {

    private boolean griefPreventionEnabled;

    public SpawnerToggle(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("default_spawn_range", 16); // Default Minecraft setting
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("spawner_toggle.toggle_message", "&7[&bSpawner&7] Mob spawning is {status} &7voor deze spawner.");
        messageMap.add("spawner_toggle.claim_restricted", "&cJe kunt deze spawner niet bewerken in andermans claim.");
        messageMap.add("spawner_toggle.status_on", "&aingeschakeld");
        messageMap.add("spawner_toggle.status_off", "&cuitgeschakeld");
        return messageMap;
    }

    @Override
    public void initialize() {
        griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        getLifecycleManager().getListenerManager().registerListener(new SpawnerInteractListener(this));
    }

    @Override
    public void disable() {

    }

    public void toggleSpawner(Player player, Block block) {
        BlockState blockState = block.getState();
        if (!(blockState instanceof CreatureSpawner spawner)) return;

        int defaultRange = (int) getConfigHandler().get("default_spawn_range");

        if (spawner.getRequiredPlayerRange() == defaultRange) {
            spawner.setRequiredPlayerRange(0);
            Component status_off = getLocalizationHandler().getMessage("spawner_toggle.status_off").forAudience(player).build();
            player.sendMessage(getLocalizationHandler().getMessage("spawner_toggle.toggle_message")
                    .forAudience(player)
                    .with("status", status_off)
                    .build());
        } else {
            spawner.setRequiredPlayerRange(defaultRange);
            Component status_on = getLocalizationHandler().getMessage("spawner_toggle.status_on").forAudience(player).build();
            player.sendMessage(getLocalizationHandler().getMessage("spawner_toggle.toggle_message")
                    .forAudience(player)
                    .with("status", status_on)
                    .build());
        }

        blockState.update();
    }

    @SuppressWarnings("deprecation")
    public boolean checkBuildPermissions(Player player, Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        return claim == null || claim.allowBreak(player, Material.SPAWNER) == null;
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

}
