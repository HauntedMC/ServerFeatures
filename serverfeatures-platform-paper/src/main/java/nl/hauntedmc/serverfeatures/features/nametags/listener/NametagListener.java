package nl.hauntedmc.serverfeatures.features.nametags.listener;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.features.skins.event.SkinUpdateEvent;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit/Paper lifecycle bridge for the nametag manager.
 */
public final class NametagListener implements Listener {
    private final Nametags feature;

    public NametagListener(Nametags feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                readyPlayer -> feature.getNametagManager().handleJoin(readyPlayer),
                "nametag player initialization"
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        feature.getNametagManager().handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTracksEntity(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player owner) {
            feature.getNametagManager().onViewerTracks(event.getPlayer(), owner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerUntracksEntity(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player owner) {
            feature.getNametagManager().onViewerUntracks(event.getPlayer(), owner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        if (feature.getNametagManager().requiresTeleportRebuild(event.getFrom(), event.getTo())) {
            feature.getNametagManager().beginPlayerTransition(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        feature.getNametagManager().beginPlayerTransition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        feature.getNametagManager().suspendForDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        feature.getNametagManager().handleRespawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkinUpdate(SkinUpdateEvent event) {
        feature.getNametagManager().rebuildOwner(event.getPlayer(), 10, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackLoaded(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            feature.getNametagManager().refreshViewer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        feature.getNametagManager().handleGameModeChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        refreshPassengerOwners(event.getEntity(), event.getMount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        refreshPassengerOwners(event.getEntity(), event.getDismounted());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            feature.getNametagManager().setGlideSuppressed(player, event.isGliding());
        }
    }

    private void refreshPassengerOwners(Entity first, Entity second) {
        if (first instanceof Player player) {
            refreshPassengerOwner(player);
        }
        if (second instanceof Player player && second != first) {
            refreshPassengerOwner(player);
        }
    }

    private void refreshPassengerOwner(Player player) {
        feature.getNametagManager().handlePassengerMutation(player);
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> {
                    if (player.isOnline()) {
                        feature.getNametagManager().updateNametag(
                                player,
                                new UpdateProperties.Builder().build()
                        );
                    }
                },
                BukkitTime.ticks(1L)
        );
    }
}
