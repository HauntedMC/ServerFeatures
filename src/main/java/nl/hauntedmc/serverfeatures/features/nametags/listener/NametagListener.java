package nl.hauntedmc.serverfeatures.features.nametags.listener;

import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import org.jetbrains.annotations.NotNull;

public class NametagListener implements Listener {

    private final Nametags feature;

    public NametagListener(Nametags feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        // Delay the creation of nametags for new players since the client might not have loaded all the entities yet.
        this.feature.getLifecycleManager().getTaskManager().scheduleDelayedTask( () -> {
            this.feature.getNametagManager().updateNametag(event.getPlayer(), new UpdateProperties.Builder().build());
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.feature.getNametagManager().removeNametag(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.feature.getNametagManager().updateNametag(event.getPlayer(), new UpdateProperties.Builder().forced(true).build());
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Player player) {
            this.feature.getNametagManager().updateNametag(player, new UpdateProperties.Builder().forced(true).delay(10L).build());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        feature.getNametagManager().updateNametag(event.getPlayer(), new UpdateProperties.Builder().forced(true).build());
    }

    @EventHandler
    public void onResourcePackSend(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            feature.getNametagManager().updateNametag(event.getPlayer(), new UpdateProperties.Builder().forced(true).build());
        }
    }

}