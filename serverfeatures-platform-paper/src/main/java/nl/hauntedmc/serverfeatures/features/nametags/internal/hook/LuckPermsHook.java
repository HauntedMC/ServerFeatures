package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes visible metadata after LuckPerms recalculates a user's cached prefix/suffix data.
 *
 * <p>The subscription is explicitly closed when the feature is disabled. LuckPerms otherwise owns
 * the listener for the lifetime of the complete ServerFeatures plugin, which would retain old
 * Nametags instances and duplicate refreshes after a feature reload.</p>
 */
public final class LuckPermsHook implements AutoCloseable {
    private final EventSubscription<UserDataRecalculateEvent> subscription;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LuckPermsHook(EventSubscription<UserDataRecalculateEvent> subscription) {
        this.subscription = subscription;
    }

    public static LuckPermsHook subscribe(Nametags feature) {
        RegisteredServiceProvider<LuckPerms> registration =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null) {
            return new LuckPermsHook(null);
        }

        EventBus eventBus = registration.getProvider().getEventBus();
        EventSubscription<UserDataRecalculateEvent> subscription = eventBus.subscribe(
                feature.getPlugin(),
                UserDataRecalculateEvent.class,
                event -> feature.getNametagManager().refreshText(event.getUser().getUniqueId(), 1)
        );
        return new LuckPermsHook(subscription);
    }

    @Override
    public void close() {
        if (subscription != null && closed.compareAndSet(false, true)) {
            subscription.close();
        }
    }
}
