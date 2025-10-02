package nl.hauntedmc.serverfeatures.features.antiraidfarm.listener;

import nl.hauntedmc.serverfeatures.features.antiraidfarm.AntiRaidFarm;
import nl.hauntedmc.serverfeatures.features.antiraidfarm.internal.AntiRaidFarmHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;

import java.util.Map;

public final class AntiRaidFarmListener implements Listener {

    private final AntiRaidFarm feature;
    private final AntiRaidFarmHandler handler;

    public AntiRaidFarmListener(AntiRaidFarm feature, AntiRaidFarmHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        final Player player = event.getPlayer();

        // Staff bypass
        if (handler.isBypassed(player)) return;

        final long now = System.currentTimeMillis();
        var remainingOpt = handler.remainingSeconds(player.getUniqueId(), now);

        if (remainingOpt.isPresent()) {
            event.setCancelled(true);
            if (handler.shouldNotify()) {
                long secs = remainingOpt.getAsLong();
                player.sendMessage(feature.getLocalizationHandler().getMessage("antiraidfarm.blocked")
                        .withPlaceholders(Map.of("seconds", String.valueOf(secs)))
                        .forAudience(player).build());
            }
            return;
        }

        // Start cooldown window
        handler.markTriggered(player.getUniqueId(), now);
    }
}
