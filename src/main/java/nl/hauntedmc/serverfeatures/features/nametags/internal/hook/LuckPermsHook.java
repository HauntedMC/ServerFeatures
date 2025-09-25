package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.log.LogReceiveEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Optional;
import java.util.UUID;

public class LuckPermsHook {

    public static void subscribeLuckPermsHook(Nametags feature) {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        LuckPerms api = provider != null ? provider.getProvider() : null;
        if (api == null) return;

        EventBus bus = api.getEventBus();

        bus.subscribe(feature.getPlugin(), LogReceiveEvent.class, e -> {
            @NonNull Optional<UUID> uuid = e.getEntry().getTarget().getUniqueId();
            if (uuid.isEmpty()) return;
            Player p = Bukkit.getPlayer(uuid.get());
            if (p != null) feature.getNametagManager().refreshText(p);
        });

        bus.subscribe(feature.getPlugin(), NodeMutateEvent.class, e -> {
            Player p = Bukkit.getPlayer(e.getTarget().getFriendlyName());
            if (p != null) feature.getNametagManager().refreshText(p);
        });
    }
}
