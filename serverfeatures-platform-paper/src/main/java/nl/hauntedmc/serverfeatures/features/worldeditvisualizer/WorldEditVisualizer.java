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

public class WorldEditVisualizer extends BukkitBaseFeature<Meta> {

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
        config.put("edge.step_blocks", 0.25);
        config.put("edge.scale", 0.15);
        config.put("corner.material", "LIME_STAINED_GLASS");
        config.put("corner.pos1_material", "BLUE_STAINED_GLASS");
        config.put("corner.pos2_material", "RED_STAINED_GLASS");
        config.put("corner.scale", 1.0);
        config.put("glow.edge_color", "aqua");
        config.put("glow.corner_color", "lime");
        config.put("glow.pos1_color", "blue");
        config.put("glow.pos2_color", "red");
        config.put("label.enabled", true);
        config.put("label.show_prefix_hash", true);
        config.put("label.scale", 0.8);
        config.put("label.y_offset", 0.8);
        config.put("render.max_distance_blocks", 128);
        config.put("render.max_entities", 1024);
        config.put("render.movement_refresh_blocks", 8);
        config.put("render.full_refresh_interval_ticks", 600);
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

    public double getDouble(String key, double fallback) {
        Object value = getConfigHandler().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public String getString(String key, String fallback) {
        Object value = getConfigHandler().get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
