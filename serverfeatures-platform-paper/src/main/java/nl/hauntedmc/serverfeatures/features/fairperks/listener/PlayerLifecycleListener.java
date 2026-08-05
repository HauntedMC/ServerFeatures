package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerLifecycleListener implements Listener {

    private final FairPerks feature;

    public PlayerLifecycleListener(FairPerks feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        feature.stateService().initializeIfAbsent(event.getPlayer());
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                () -> feature.stateService().reconcileEnvironment(event.getPlayer())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        feature.stateService().remove(event.getPlayer());
        feature.clearFeedback(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        feature.stateService().reconcileEnvironment(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                () -> feature.stateService().reconcileEnvironment(event.getPlayer())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                () -> feature.stateService().reconcileAfterRespawn(event.getPlayer())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        feature.stateService().clearFallDamageGrace(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerMob(CreatureSpawnEvent event) {
        boolean shouldMarkSpawnerMobs = feature.settings().hostiles().spawnerMobsExempt()
                || feature.settings().hostiles().markSpawnerMobs();
        if (shouldMarkSpawnerMobs
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                && feature.hostileClassifier().isHostile(event.getEntity())) {
            feature.hostileClassifier().markSpawnerMob(event.getEntity());
        }
    }
}
