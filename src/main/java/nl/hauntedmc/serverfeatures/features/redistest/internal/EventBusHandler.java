package nl.hauntedmc.serverfeatures.features.redistest.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.redistest.RedisTest;
import org.bukkit.Bukkit;

public class EventBusHandler {


    private final MessagingDataAccess redisBus;
    private final String serverName;
    private final RedisTest feature;
    private Subscription chatSubscription;


    public EventBusHandler(RedisTest feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
        this.serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");

    }

    public void subscribeChannel(String channel) {
        try {
        chatSubscription = redisBus.subscribe(
                channel,
                ChatMessage.class,
                this::handleIncoming
        );
    } catch (Exception ex) {
        feature.getPlugin().getLogger().severe("RedisTest: failed to subscribe to channel");
        ex.printStackTrace();
    }}


    private void handleIncoming(ChatMessage cm) {
        if (serverName.equals(cm.getServer())) return;

        String out = String.format("§a%s §b%s§f: %s",
                cm.getServer(), cm.getPlayer(), cm.getMessage());

        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage(Component.text(out))
        );
    }

    public void disable() {
        if (chatSubscription != null) {
            chatSubscription.unsubscribe();
            chatSubscription = null;
        }
    }

    public void publishMessage(ChatMessage chatMessage, String channel) {
        redisBus.publish(channel , chatMessage)
                    .exceptionally(ex -> {
                        feature.getPlugin().getLogger().severe("RedisTest: failed to publish chat");
                        return null;
                    });
        }
}
