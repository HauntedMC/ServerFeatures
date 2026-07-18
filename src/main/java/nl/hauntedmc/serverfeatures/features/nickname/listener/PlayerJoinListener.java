package nl.hauntedmc.serverfeatures.features.nickname.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private static final BukkitTime DATA_REGISTRY_WARMUP_DELAY = BukkitTime.ticks(6L);

    private final Nickname feature;

    public PlayerJoinListener(Nickname feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    feature.getNicknameHandler().loadNicknameIntoCache(player);
                },
                DATA_REGISTRY_WARMUP_DELAY
        );
    }
}
