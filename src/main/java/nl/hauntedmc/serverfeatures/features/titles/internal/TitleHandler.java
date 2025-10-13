package nl.hauntedmc.serverfeatures.features.titles.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.titles.Titles;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

public class TitleHandler {

    private final Titles feature;
    private final int fadeIn;
    private final int fadeOut;
    private final int stay;
    private final int delay;

    public TitleHandler(Titles feature) {
        this.feature = feature;
        fadeIn = (int) feature.getConfigHandler().getSetting("fade-in");
        stay =  (int) feature.getConfigHandler().getSetting("stay");
        fadeOut =  (int) feature.getConfigHandler().getSetting("fade-out");
        delay = (int) feature.getConfigHandler().getSetting("delay");
    }

    public void sendJoinTitle(Player player) {
        Component title = feature.getLocalizationHandler().getMessage("titles.join_title").forAudience(player).build();
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        Component subtitle = feature.getLocalizationHandler().getMessage("titles.join_subtitle").forAudience(player).with("server", serverName).build();
        Title.Times times = Title.Times.times(Duration.ofMillis(50L *fadeIn), Duration.ofMillis(50L * stay), Duration.ofMillis(50L * fadeOut));
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                    player.sendTitlePart(TitlePart.TIMES, times);
                    player.sendTitlePart(TitlePart.SUBTITLE, subtitle);
                    player.sendTitlePart(TitlePart.TITLE, title);
        }, BukkitTime.ticks(delay));
    }
}
