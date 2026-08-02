package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.entity.Player;

import java.util.Arrays;

/** Lifecycle handle for one viewer's client-only display entities. */
final class PacketVisualHandle {

    private final int[] entityIds;
    private boolean cleared;

    PacketVisualHandle(int[] entityIds) {
        this.entityIds = Arrays.copyOf(entityIds, entityIds.length);
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
