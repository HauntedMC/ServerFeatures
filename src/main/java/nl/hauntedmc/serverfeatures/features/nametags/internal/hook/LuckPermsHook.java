package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.log.LogReceiveEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Optional;
import java.util.UUID;

public class LuckPermsHook {

    public static void subscribeLuckPermsHook(Nametags feature) {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        LuckPerms api = null;

        if (provider != null) {
            api = provider.getProvider();

        }

        if (api != null) {
            EventBus eventBus = api.getEventBus();

            eventBus.subscribe(feature.getPlugin(), LogReceiveEvent.class, e -> {
                @NonNull Optional<UUID> uuid = e.getEntry().getTarget().getUniqueId();
                if (uuid.isEmpty()) {
                    return;
                }
                Player player = Bukkit.getPlayer(uuid.get());
                if (player != null) {
                    if (feature.getNametagManager().getRegisteredPlayers().contains(player)) {
                        feature.getNametagManager().updateNametag(player, new UpdateProperties.Builder().forced(true).updateText(true).delay(20L).build());
                    }
                }

            });
            eventBus.subscribe(feature.getPlugin(), NodeMutateEvent.class, e -> {
                Player player = Bukkit.getPlayer(e.getTarget().getFriendlyName());
                if (player != null) {
                    if (feature.getNametagManager().getRegisteredPlayers().contains(player)) {
                        feature.getNametagManager().updateNametag(player, new UpdateProperties.Builder().forced(true).updateText(true).build());
                    }
                }
            });

        }
    }

}