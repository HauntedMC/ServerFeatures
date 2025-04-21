package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import nl.hauntedmc.serverfeatures.features.bossbar.Bossbars;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossbarRegistry {

    private final Bossbars feature;
    private final List<BossbarMessage> messages = new ArrayList<>();

    public BossbarRegistry(Bossbars feature) {
        this.feature = feature;
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        Object raw = feature.getConfigHandler().getSetting("messages");
        if (raw instanceof List<?> messageList) {
            for (Object obj : messageList) {
                if (obj instanceof Map<?, ?> map) {
                    String key = map.get("message_key").toString();

                    long duration;
                    try {
                        duration = Long.parseLong(map.get("duration").toString());
                    } catch (NumberFormatException e) {
                        duration = 100L;
                    }

                    BarColor color;
                    try {
                        color = BarColor.valueOf(map.get("color").toString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        color = BarColor.WHITE;
                    }

                    BarStyle style;
                    try {
                        style = BarStyle.valueOf(map.get("style").toString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        style = BarStyle.SOLID;
                    }

                    boolean autoFade = Boolean.parseBoolean(map.get("autoFade").toString());

                    BossbarMessage message = new BossbarMessage.Builder()
                            .messageKey(key)
                            .durationTicks(duration)
                            .color(color)
                            .style(style)
                            .autoFade(autoFade)
                            .initialProgress(1.0)
                            .build();
                    messages.add(message);
                }
            }
        }
    }

    public List<BossbarMessage> getMessages() {
        return messages;
    }

    public BossbarMessage get(int currentMessageIndex) {
        if (messages.isEmpty()) {
            return new BossbarMessage.Builder().messageKey("default").durationTicks(100)
                    .color(BarColor.WHITE).style(BarStyle.SOLID).build();
        }

        return messages.get(currentMessageIndex);
    }

    public int getTotalMessages() {
        return messages.size();
    }
}
