package nl.hauntedmc.serverfeatures.features.holograms;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.holograms.internal.HologramHandler;
import nl.hauntedmc.serverfeatures.features.holograms.listener.HologramListener;
import nl.hauntedmc.serverfeatures.features.holograms.meta.Meta;
import nl.hauntedmc.serverfeatures.features.holograms.registry.HologramRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modern Holograms feature based on TextDisplay entities.
 *
 * Lines are NOT defined in config. Instead, we read sequential message keys:
 *   holograms.hologram.<id>.0
 *   holograms.hologram.<id>.1
 *   ...
 * until a key is missing; or a line contains the <end> marker (which is removed from output).
 */
public final class Holograms extends BukkitBaseFeature<Meta> {

    private HologramRegistry registry;
    private HologramHandler handler;

    public Holograms(ServerFeatures plugin) { super(plugin, new Meta()); }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);

        Map<String, Object> holograms = new LinkedHashMap<>();

        holograms.put("spawn", mapOf(
                "world", "world",
                "x", 0.5D,
                "y", 65.0D,
                "z", 0.5D,
                "yaw", 0.0F,
                "pitch", 0.0F,
                "billboard", "CENTER",        // FIXED, CENTER, VERTICAL, HORIZONTAL
                "alignment", "CENTER",        // LEFT, CENTER, RIGHT
                "line_width", 220,            // pixels; 0 or missing = no wrapping
                "see_through", true,
                "shadowed", true,
                "use_default_background", false,
                "background_color", "#66000000", // AARRGGBB or RRGGBB (assumes FF alpha if missing)
                "glow", false,
                "glow_color", "#00FFFF",
                "view_range", 32.0F,
                "brightness", mapOf("block", 15, "sky", 15)
        ));

        cfg.put("holograms", holograms);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap map = new MessageMap();

        // Example lines for hologram "spawn"
        map.add("holograms.hologram.spawn.0", "&b&lWelkom");
        map.add("holograms.hologram.spawn.1", "");
        map.add("holograms.hologram.spawn.2", "&aSucces");
        map.add("holograms.hologram.spawn.3", "");
        map.add("holograms.hologram.spawn.4", "&7Doei!<end>");

        return map;
    }

    @Override
    public void initialize() {
        this.registry = new HologramRegistry(this);
        this.handler = new HologramHandler(this, registry);

        getLifecycleManager().getListenerManager().registerListener(new HologramListener(this));
        // spawn after enable (ensure worlds ready)
        getLifecycleManager().getTaskManager().scheduleOneTimeTask(handler::spawnAllSafe);
    }

    @Override
    public void disable() {
        if (handler != null) handler.removeAll();
    }

    public HologramRegistry getRegistry() { return registry; }
    public HologramHandler getHandler() { return handler; }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
