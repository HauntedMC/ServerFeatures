package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.listener.SpawnerInteractListener;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;

import java.util.Objects;

public class SpawnerToggle extends BukkitBaseFeature<Meta> {

    private SpawnerVisualService visualService;

    public SpawnerToggle(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("toggle_permission", "");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add(
                "spawner_toggle.toggle_message",
                "&7[&bSpawner&7] Mob spawning is {status} &7voor deze spawner."
        );
        messageMap.add(
                "spawner_toggle.claim_restricted",
                "&cJe kunt deze spawner niet bewerken in andermans claim."
        );
        messageMap.add("spawner_toggle.status_on", "&aingeschakeld");
        messageMap.add("spawner_toggle.status_off", "&cuitgeschakeld");
        return messageMap;
    }

    @Override
    public void initialize() {
        visualService = new SpawnerVisualService(this::isDisabled);
        getLifecycleManager().getListenerManager().registerListener(new SpawnerInteractListener(this));
        getLifecycleManager().getTaskManager().scheduleDelayedTask(
                this::refreshLoadedVisuals,
                BukkitTime.ticks(1)
        );
    }

    @Override
    public void disable() {
        SpawnerVisualService service = visualService;
        if (service != null) {
            restoreLoadedVisuals(service);
            visualService = null;
        }
    }

    public void toggleSpawner(Player player, Block block) {
        BlockState blockState = block.getState();
        if (!(blockState instanceof CreatureSpawner spawner)) {
            return;
        }

        boolean disabled = !SpawnerToggleState.isDisabled(spawner, getPlugin());
        SpawnerToggleState.setDisabled(spawner, getPlugin(), disabled);
        spawner.update(true, false);
        visualService().refresh(spawner);

        Component status = getLocalizationHandler()
                .getMessage(disabled
                        ? "spawner_toggle.status_off"
                        : "spawner_toggle.status_on")
                .forAudience(player)
                .build();
        player.sendMessage(getLocalizationHandler()
                .getMessage("spawner_toggle.toggle_message")
                .forAudience(player)
                .with("status", status)
                .build());
    }

    public boolean isDisabled(CreatureSpawner spawner) {
        return SpawnerToggleState.isDisabled(spawner, getPlugin());
    }

    public void refreshChunkVisuals(Player viewer, Chunk chunk) {
        SpawnerVisualService service = visualService;
        if (service != null) {
            service.refreshChunk(viewer, chunk);
        }
    }

    public boolean mayToggle(Player player) {
        String permission = getConfigHandler()
                .node("toggle_permission")
                .as(String.class, "")
                .trim();
        return permission.isEmpty() || player.hasPermission(permission);
    }

    @SuppressWarnings("deprecation")
    public boolean checkBuildPermissions(Player player, Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        return claim == null || claim.allowBreak(player, Material.SPAWNER) == null;
    }

    public boolean isGriefPreventionEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
    }

    private SpawnerVisualService visualService() {
        return Objects.requireNonNull(visualService, "SpawnerToggle is not initialized");
    }

    private void refreshLoadedVisuals() {
        SpawnerVisualService service = visualService;
        if (service != null) {
            forEachLoadedDisabledSpawner(service::refresh);
        }
    }

    private void restoreLoadedVisuals(SpawnerVisualService service) {
        forEachLoadedDisabledSpawner(service::restoreActual);
    }

    private void forEachLoadedDisabledSpawner(
            java.util.function.Consumer<CreatureSpawner> action
    ) {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof CreatureSpawner spawner && isDisabled(spawner)) {
                        action.accept(spawner);
                    }
                }
            }
        }
    }
}
