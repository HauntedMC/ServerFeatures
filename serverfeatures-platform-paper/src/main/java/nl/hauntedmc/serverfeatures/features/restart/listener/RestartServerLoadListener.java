package nl.hauntedmc.serverfeatures.features.restart.listener;

import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartLifecyclePublisher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/** Publishes READY only when Paper reports that startup or reload has completed. */
public final class RestartServerLoadListener implements Listener {

    private final RestartLifecyclePublisher publisher;

    public RestartServerLoadListener(RestartLifecyclePublisher publisher) {
        this.publisher = publisher;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            publisher.publishReadyAfterServerLoad();
        }
    }
}
