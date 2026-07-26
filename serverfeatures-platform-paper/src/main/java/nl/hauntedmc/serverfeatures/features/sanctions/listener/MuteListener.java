package nl.hauntedmc.serverfeatures.features.sanctions.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.serverfeatures.features.sanctions.service.SanctionsDataService;
import nl.hauntedmc.serverfeatures.features.sanctions.state.MuteRegistry;
import nl.hauntedmc.serverfeatures.features.sanctions.state.MuteRegistry.MuteState;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public class MuteListener implements Listener {

    private final Sanctions feature;
    private final MuteRegistry registry;
    private final SanctionsDataService service;

    public MuteListener(Sanctions feature, MuteRegistry registry, SanctionsDataService service) {
        this.feature = feature;
        this.registry = registry;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        restoreMuteState(e.getPlayer());
    }

    public void restoreMuteState(Player player) {
        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                (readyPlayer, identity) -> feature.getLifecycleManager()
                        .getTaskManager()
                        .runAsync(() -> registry.trackIfMuted(
                                readyPlayer.getUniqueId(),
                                identity.playerId()
                        ))
                        .exceptionally(throwable -> {
                            feature.getLogger().warning(
                                    "Could not restore mute state for " + readyPlayer.getName()
                                            + ": " + rootMessage(throwable)
                            );
                            return null;
                        }),
                "mute restoration"
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        registry.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!registry.isMuted(uuid)) return;

        event.setCancelled(true);

        // Gentle throttle of the feedback message
        if (!registry.shouldNotify(uuid)) return;

        MuteState ms = registry.get(uuid).orElse(null);
        if (ms == null) return;

        String reason = ms.reason() == null || ms.reason().isBlank() ? "-" : ms.reason();

        String key;
        if (ms.isPermanent()) {
            key = "sanctions.chat_blocked.perm";
            Component msg = feature.getLocalizationHandler().getMessage(key)
                    .with("reason", reason)
                    .forAudience(player)
                    .build();
            player.sendMessage(msg);
        } else {
            String remaining = service.remaining(Instant.now(), ms.expiresAt());
            key = "sanctions.chat_blocked.temp";
            Component msg = feature.getLocalizationHandler().getMessage(key)
                    .with("remaining", remaining)
                    .with("reason", reason)
                    .forAudience(player)
                    .build();
            player.sendMessage(msg);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current.getCause() != null)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
