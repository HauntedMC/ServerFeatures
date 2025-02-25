package nl.hauntedmc.serverfeatures.features.bossbar;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.bossbar.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.bossbar.internal.BossbarHandler;
import nl.hauntedmc.serverfeatures.features.bossbar.listener.BossbarListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bossbars extends BaseFeature<Meta> {

    private BossbarHandler bossbarHandler;

    public Bossbars(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);

        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("text", "&6Welcome to HauntedMC!");
        msg1.put("duration", 100);
        msg1.put("color", "YELLOW");
        msg1.put("style", "SOLID");
        msg1.put("autoFade", true);
        messages.add(msg1);

        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("text", "&aEnjoy your stay!");
        msg2.put("duration", 100);
        msg2.put("color", "GREEN");
        msg2.put("style", "SEGMENTED_6");
        msg2.put("autoFade", false);
        messages.add(msg2);

        defaults.put("messages", messages);

        Map<String, Object> animation = new HashMap<>();
        animation.put("steps_per_second", 20);
        animation.put("fade_delay", 0);
        defaults.put("animation", animation);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
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
