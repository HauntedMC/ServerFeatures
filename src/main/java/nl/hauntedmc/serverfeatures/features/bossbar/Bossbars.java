package nl.hauntedmc.serverfeatures.features.bossbar;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.bossbar.internal.BossbarHandler;
import nl.hauntedmc.serverfeatures.features.bossbar.listener.BossbarListener;
import nl.hauntedmc.serverfeatures.features.bossbar.meta.Meta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Bossbars extends BukkitBaseFeature<Meta> {

    private BossbarHandler bossbarHandler;

    public Bossbars(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        Map<String, Object> messages = new LinkedHashMap<>();

        Map<String, Object> text1 = new LinkedHashMap<>();
        text1.put("duration", 100);
        text1.put("color", "YELLOW");
        text1.put("style", "SOLID");
        text1.put("autoFade", true);
        text1.put("initialProgress", 1.0);
        messages.put("text1", text1);

        Map<String, Object> text2 = new LinkedHashMap<>();
        text2.put("duration", 100);
        text2.put("color", "GREEN");
        text2.put("style", "SEGMENTED_6");
        text2.put("autoFade", false);
        text2.put("initialProgress", 1.0);
        messages.put("text2", text2);

        defaults.put("messages", messages);

        Map<String, Object> animation = new LinkedHashMap<>();
        animation.put("steps_per_second", 20);
        animation.put("fade_delay", 0);
        defaults.put("animation", animation);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap map = new MessageMap();
        map.add("bossbar.text1", "Bossbar text1");
        map.add("bossbar.text2", "Bossbar text2");
        return map;
    }

    @Override
    public void initialize() {
        this.bossbarHandler = new BossbarHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new BossbarListener(this));

        bossbarHandler.initOnlinePlayers();
        bossbarHandler.startMessageCycle();
    }

    @Override
    public void disable() {
        bossbarHandler.removeAllBossbars();
    }

    public BossbarHandler getBossbarHandler() {
        return bossbarHandler;
    }
}
