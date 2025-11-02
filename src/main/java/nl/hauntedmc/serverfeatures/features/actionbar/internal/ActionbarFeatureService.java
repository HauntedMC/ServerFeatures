package nl.hauntedmc.serverfeatures.features.actionbar.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.*;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

/**
 * Builds cycles from config and talks to the global ActionBars API.
 */
public final class ActionbarFeatureService {

    private final Actionbar feature;
    private ActionBarCycleHandle cycleHandle;

    public ActionbarFeatureService(@NotNull Actionbar feature) {
        this.feature = feature;
    }

    public boolean isCycleRunning() {
        return cycleHandle != null && cycleHandle.isActive();
    }

    public void startCycle() {
        if (isCycleRunning()) return;
        ActionBarCycle cycle = buildCycleFromConfig();
        cycleHandle = ActionBars.service().startCycle(cycle);
    }

    public void stopCycle() {
        if (cycleHandle != null) {
            cycleHandle.cancel();
            cycleHandle = null;
        }
    }

    /**
     * Send one-shot or timed broadcast. Timed messages pause the cycle while active.
     */
    public void sendManual(String userInputMiniMessage, int seconds) {
        Function<Player, Component> perPlayer = p -> {
            String mm = TextFormatter.convert(userInputMiniMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .preprocess(s -> PlaceholderAPIHook.applyPlaceholders(s, p))
                    .toMiniMessage();

            return ComponentFormatter.deserialize(mm)
                    .expect(TextFormatter.InputFormat.MINIMESSAGE)
                    .features(ComponentFormatter.ALL_DEFAULTS())
                    .autoLinkUrls()
                    .toComponent();
        };

        if (seconds <= 0) {
            ActionBars.service().sendOnceBroadcastPerPlayer(perPlayer);
        } else {
            ActionBars.service().sendBroadcastPerPlayer(perPlayer, seconds, PauseMode.PAUSE_CYCLE);
        }
    }

    /* ------------------------- config → cycle ------------------------- */

    private @NotNull ActionBarCycle buildCycleFromConfig() {
        ActionBarCycle.Builder b = ActionBarCycle.builder();

        ConfigNode root = feature.getConfigHandler().node("messages");
        Map<String, ConfigNode> children = root.children();

        if (children.isEmpty()) {
            // default 100 ticks → 5s
            int seconds = ceilTicksToSeconds(100);
            b.add(ActionBarEntry.perPlayer(p ->
                    feature.getLocalizationHandler().getMessage("actionbar.default").forAudience(p).build(), seconds));
        } else {
            for (Map.Entry<String, ConfigNode> e : children.entrySet()) {
                String id = e.getKey();
                ConfigNode n = e.getValue();

                String key = n.get("message_key").as(String.class, id);
                long durationTicks = n.get("duration").as(Long.class, 100L);
                int seconds = ceilTicksToSeconds(durationTicks);

                b.add(ActionBarEntry.perPlayer(p ->
                                feature.getLocalizationHandler().getMessage("actionbar." + key).forAudience(p).build(),
                        Math.max(0, seconds)));
            }
        }

        int intervalTicks = (int) feature.getConfigHandler().get("message_interval");
        b.gapSeconds(Math.max(0, ceilTicksToSeconds(intervalTicks)));
        return b.build();
    }

    private static int ceilTicksToSeconds(long ticks) {
        if (ticks <= 0) return 0;
        return (int) ((ticks + 19) / 20);
    }
}
