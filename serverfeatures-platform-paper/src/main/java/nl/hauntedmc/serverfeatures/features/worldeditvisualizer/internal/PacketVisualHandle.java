package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/** Lifecycle handle for one viewer's client-only display entities. */
final class PacketVisualHandle {

    private final UUID worldId;
    private final int[] entityIds;
    private boolean cleared;

    PacketVisualHandle(UUID worldId, int[] entityIds) {
        this.worldId = worldId;
        this.entityIds = Arrays.copyOf(entityIds, entityIds.length);
    }

    UUID worldId() {
        return worldId;
    }

    int entityCount() {
        return entityIds.length;
    }

    void clear(Player viewer) {
        if (cleared) {
            return;
        }
        cleared = true;
        if (!viewer.isOnline() || entityIds.length == 0) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerDestroyEntities(entityIds)
        );
    }

    void discard() {
        cleared = true;
    }
}
