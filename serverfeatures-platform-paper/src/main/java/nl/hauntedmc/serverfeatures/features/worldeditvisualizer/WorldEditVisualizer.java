package nl.hauntedmc.serverfeatures.features.worldeditvisualizer;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command.WorldEditVisualizerCommand;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.listener.PlayerLifecycleListener;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta.Meta;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldEditVisualizer extends BukkitBaseFeature<Meta> {

    private static final List<String> OBSOLETE_CONFIG_KEYS = List.of(
            "glow",
            "label",
            "edge.scale",
            "corner.scale"
    );

    private VisualizationService service;

    public WorldEditVisualizer(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);
        config.put("auto_enable_on_join", true);
        config.put("edge.material", "WHITE_STAINED_GLASS");
        config.put("corner.material", "LIME_STAINED_GLASS");
        config.put("corner.pos1_material", "BLUE_STAINED_GLASS");
        config.put("corner.pos2_material", "RED_STAINED_GLASS");
        config.put("edge.step_blocks", 1);
        config.put("render.max_distance_blocks", 128);
        config.put("render.max_blocks", 2048);
        config.put("render.refresh_interval_ticks", 100);
        config.put("poll.interval_ticks", 10);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("worldeditvisualizer.enabled",
                "&aVisualizer ingeschakeld. Alleen jij ziet de packetweergave.");
        messages.add("worldeditvisualizer.disabled", "&7Visualizer uitgeschakeld en gewist.");
        messages.add("worldeditvisualizer.refreshed", "&aWorldEdit-selectie opnieuw weergegeven.");
        messages.add("worldeditvisualizer.no_selection", "&eGeen complete WorldEdit-selectie gevonden.");
        messages.add("worldeditvisualizer.not_cuboid", "&eAlleen cuboid-selecties worden ondersteund.");
        messages.add("worldeditvisualizer.failed", "&cDe visualisatie kon niet worden bijgewerkt. Zie de console.");
        messages.add("worldeditvisualizer.usage", "&eGebruik: /wevis [toggle|on|off|refresh]");
        return messages;
    }

    @Override
    public void initialize() {
        removeObsoleteConfig();
        service = new VisualizationService(this);

        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new WorldEditVisualizerCommand(this, service));
        getLifecycleManager().getListenerManager()
                .registerListener(new PlayerLifecycleListener(service));

        if (getBoolean("auto_enable_on_join", true)) {
            for (Player player : getPlugin().getServer().getOnlinePlayers()) {
                if (player.hasPermission(VisualizationService.USE_PERMISSION)) {
                    service.enable(player);
                }
            }
        }

        int interval = Math.max(1, getInt("poll.interval_ticks", 10));
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                service::pollSelections,
                BukkitTime.ticks(interval)
        );
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    private void removeObsoleteConfig() {
        List<String> present = OBSOLETE_CONFIG_KEYS.stream()
                .filter(key -> getConfigHandler().get(key) != null)
                .toList();
        if (present.isEmpty()) {
            return;
        }
        getConfigHandler().batch(batch -> present.forEach(batch::remove));
        getPlugin().getLogger().info(
                "[WorldEditVisualizer] Removed obsolete config keys: " + String.join(", ", present)
        );
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = getConfigHandler().get(key);
        return value instanceof Boolean configured ? configured : fallback;
    }

    public int getInt(String key, int fallback) {
        Object value = getConfigHandler().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public String getString(String key, String fallback) {
        Object value = getConfigHandler().get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
