package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads bossbar messages from local/bossbars.yml (root key: "messages").
 */
public class BossbarRegistry {

    private final Bossbars feature;
    private final ConfigView store; // local/bossbars.yml
    private final List<BossbarMessage> messages = new ArrayList<>();

    public BossbarRegistry(Bossbars feature) {
        this.feature = feature;
        this.store = new ConfigService(feature.getPlugin()).view("local/bossbars.yml", /*copyDefaultsIfPresent*/ true);
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        messages.clear();

        ConfigNode root = store.node("messages");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            String id = entry.getKey();
            ConfigNode n = entry.getValue();

            String key = n.get("message_key").as(String.class, id); // default to id
            long durationTicks = n.get("duration").as(Long.class, 100L);
            BarColor color = n.get("color").as(BarColor.class, BarColor.WHITE);
            BarStyle style = n.get("style").as(BarStyle.class, BarStyle.SOLID);
            boolean autoFade = n.get("autoFade").as(Boolean.class, false);
            double initialProg = n.get("initialProgress").as(Double.class, 1.0);

            if (initialProg < 0.0) initialProg = 0.0;
            if (initialProg > 1.0) initialProg = 1.0;

            BossbarMessage message = new BossbarMessage.Builder()
                    .messageKey(key)
                    .durationTicks(durationTicks)
                    .color(color)
                    .style(style)
                    .autoFade(autoFade)
                    .initialProgress(initialProg)
                    .build();

            messages.add(message);
        }
    }

    public List<BossbarMessage> getMessages() {
        return messages;
    }

    public BossbarMessage get(int currentMessageIndex) {
        if (messages.isEmpty()) {
            return new BossbarMessage.Builder()
                    .messageKey("default")
                    .durationTicks(100L)
                    .color(BarColor.WHITE)
                    .style(BarStyle.SOLID)
                    .initialProgress(1.0)
                    .build();
        }
        return messages.get(currentMessageIndex);
    }

    public int getTotalMessages() {
        return messages.size();
    }
}
