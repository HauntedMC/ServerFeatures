package nl.hauntedmc.serverfeatures.features.worldeditvisualizer;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command.WorldEditVisualizerCommand;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.listener.PlayerJoinListener;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.meta.Meta;
import org.bukkit.entity.Player;

public class WorldEditVisualizer extends BukkitBaseFeature<Meta> {

    private VisualizationService service;

    public WorldEditVisualizer(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap c = new ConfigMap();
        c.put("enabled", false);
        c.put("edge.material", "WHITE_STAINED_GLASS");
        c.put("corner.material", "LIME_STAINED_GLASS");
        c.put("corner.pos1_material", "BLUE_STAINED_GLASS");
        c.put("corner.pos2_material", "RED_STAINED_GLASS");
        c.put("glow.edge_color", "aqua");
        c.put("glow.corner_color", "aqua");
        c.put("glow.pos1_color", "blue");
        c.put("glow.pos2_color", "red");
        c.put("edge.step_blocks", 0.25d);
        c.put("edge.scale", 0.15d);
        c.put("corner.scale", 1.0d);
        c.put("label.enabled", true);
        c.put("label.y_offset", 0.7d);
        c.put("label.scale", 1.0d);
        c.put("label.show_prefix_hash", false); // if true, show "#1"/"#2" instead of "pos1"/"pos2"
        c.put("poll.interval_ticks", 10);
        return c;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("worldeditvisualizer.enabled", "&aVisualizer ingeschakeld. Je WorldEdit-selectie wordt getoond.");
        m.add("worldeditvisualizer.disabled", "&7Visualizer uitgeschakeld en gewist.");
        m.add("worldeditvisualizer.no_selection", "&eGeen WorldEdit-selectie gevonden.");
        m.add("worldeditvisualizer.not_cuboid", "&eAlleen cuboid-selecties worden ondersteund.");
        return m;
    }

    @Override
    public void initialize() {
        this.service = new VisualizationService(this);

        // Command
        getLifecycleManager()
                .getCommandManager()
                .registerFeatureCommand(new WorldEditVisualizerCommand(this, service));

        // Events
        getLifecycleManager().getListenerManager().registerListener(new PlayerJoinListener(service));

        // Enable by default for online players with permission
        for (Player p : getPlugin().getServer().getOnlinePlayers()) {
            if (p.hasPermission("serverfeatures.feature.worldeditvisualizer.use")) {
                service.enable(p);
            }
        }

        // Poll loop
        int interval = getInt("poll.interval_ticks", 10);
        if (interval > 0) {
            getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                    service::pollSelections,
                    BukkitTime.ticks(interval)
            );
        }
    }

    @Override
    public void disable() {
        if (service != null) {
            for (Player p : getPlugin().getServer().getOnlinePlayers()) {
                service.clear(p);
            }
            service = null;
        }
    }

    /* helpers */
    public boolean getBoolean(String key, boolean def) {
        Object v = getConfigHandler().get(key);
        return (v instanceof Boolean b) ? b : def;
    }

    public int getInt(String key, int def) {
        Object v = getConfigHandler().get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Throwable ignored) {
        }
        return def;
    }

    public double getDouble(String key, double def) {
        Object v = getConfigHandler().get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Throwable ignored) {
        }
        return def;
    }

    public String getString(String key, String def) {
        Object v = getConfigHandler().get(key);
        return v == null ? def : String.valueOf(v);
    }
}
