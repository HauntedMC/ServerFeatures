package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Refreshes visible metadata after LuckPerms recalculates a user's cached prefix/suffix data.
 */
public final class LuckPermsHook {
    private LuckPermsHook() {
    }

    public static void subscribeLuckPermsHook(Nametags feature) {
        RegisteredServiceProvider<LuckPerms> registration =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null) {
            return;
        }

        EventBus eventBus = registration.getProvider().getEventBus();
        eventBus.subscribe(feature.getPlugin(), UserDataRecalculateEvent.class, event ->
                feature.getNametagManager().refreshText(event.getUser().getUniqueId(), 1)
        );
    }
}
