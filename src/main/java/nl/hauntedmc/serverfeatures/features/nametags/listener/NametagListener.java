package nl.hauntedmc.serverfeatures.features.nametags.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.features.skins.event.SkinUpdateEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

public class NametagListener implements Listener {

    private final Nametags feature;

    public NametagListener(Nametags feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        feature.getNametagManager().preloadSelfView(event.getPlayer());
        // Delay the creation of nametags for new players since the client might not have loaded all the entities yet.
        this.feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> this.feature.getNametagManager().updateNametag(event.getPlayer(), new UpdateProperties.Builder().build()),
                BukkitTime.ticks(10L)
        );
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.feature.getNametagManager().removeNametag(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.feature.getNametagManager().updateNametag(event.getPlayer(),
                new UpdateProperties.Builder().forced(true).delay(10L).build());
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Player player) {
            this.feature.getNametagManager().updateNametag(player,
                    new UpdateProperties.Builder().forced(true).delay(10L).build());
        }
    }

    @EventHandler
    public void onSkinUpdate(SkinUpdateEvent event) {
        this.feature.getNametagManager().updateNametag(event.getPlayer(),
                new UpdateProperties.Builder().forced(true).delay(10L).build());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        feature.getNametagManager().updateNametag(event.getPlayer(),
                new UpdateProperties.Builder().forced(true).build());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;

        double distance = event.getFrom().distance(event.getTo());

        if (distance > 80) {
            this.feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> this.feature.getNametagManager().updateNametag(event.getPlayer(),
                            new UpdateProperties.Builder().ownerOnly(true).build()),
                    BukkitTime.ticks(5L)
            );
        }
    }

    @EventHandler
    public void onResourcePackSend(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            feature.getNametagManager().updateNametag(event.getPlayer(),
                    new UpdateProperties.Builder().forced(true).build());
        }
    }

    @EventHandler
    public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player p) {
            feature.getNametagManager().setGlideSuppressed(p, event.isGliding());
        }
    }
}
