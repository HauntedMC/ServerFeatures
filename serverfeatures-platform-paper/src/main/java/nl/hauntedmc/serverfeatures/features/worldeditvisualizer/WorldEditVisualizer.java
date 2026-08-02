package nl.hauntedmc.serverfeatures.features.worldeditvisualizer;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command.WorldEditVisualizerCommand;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.listener.PlayerJoinListener;
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
        config.put("edge.material", "WHITE_STAINED_GLASS");
        config.put("corner.material", "LIME_STAINED_GLASS");
        config.put("corner.pos1_material", "BLUE_STAINED_GLASS");
        config.put("corner.pos2_material", "RED_STAINED_GLASS");
        config.put("edge.step_blocks", 1);
        config.put("render.max_blocks", 2048);
        config.put("render.max_distance_blocks", 192);
        config.put("render.resend_interval_ticks", 100);
        config.put("poll.interval_ticks", 10);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("worldeditvisualizer.enabled", "&aVisualizer ingeschakeld. Je WorldEdit-selectie wordt getoond.");
        messages.add("worldeditvisualizer.disabled", "&7Visualizer uitgeschakeld en gewist.");
        messages.add("worldeditvisualizer.no_selection", "&eGeen volledige WorldEdit cuboid-selectie gevonden.");
        messages.add("worldeditvisualizer.not_cuboid", "&eAlleen cuboid-selecties worden ondersteund.");
        return messages;
    }

    @Override
    public void initialize() {
        this.service = new VisualizationService(this);

        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new WorldEditVisualizerCommand(this, service));
        getLifecycleManager().getListenerManager().registerListener(new PlayerJoinListener(service));

        for (Player player : getPlugin().getServer().getOnlinePlayers()) {
            if (player.hasPermission("serverfeatures.feature.worldeditvisualizer.use")) {
                service.enable(player);
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
