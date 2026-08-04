package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import org.bukkit.entity.Player;

/**
 * Converts client interaction packets for virtual grave hitboxes into main-thread claim attempts.
 */
public final class GraveInteractionPacketListener extends PacketListenerAbstract {
    private final Graveyard feature;
    private final GraveManager manager;

    public GraveInteractionPacketListener(Graveyard feature, GraveManager manager) {
        super(PacketListenerPriority.HIGHEST);
        this.feature = feature;
        this.manager = manager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        int entityId = wrapper.getEntityId();
        if (!manager.isInteractionEntity(entityId)) {
            return;
        }

        // Vanilla cannot resolve a packet-only entity. Consume every packet targeting one of our
        // interaction IDs, then perform all authoritative validation on the Bukkit main thread.
        event.setCancelled(true);
        if (!isClaimInteraction(wrapper.getAction())) {
            return;
        }
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                () -> manager.handleInteraction(player, entityId)
        );
    }

    static boolean isClaimInteraction(WrapperPlayClientInteractEntity.InteractAction action) {
        return action == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                || action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
                || action == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
    }
}
