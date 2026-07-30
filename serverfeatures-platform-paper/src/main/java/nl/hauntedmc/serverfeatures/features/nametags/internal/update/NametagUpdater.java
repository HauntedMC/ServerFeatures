package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

import nl.hauntedmc.serverfeatures.api.io.packet.BundleDelimiterPacket;
import nl.hauntedmc.serverfeatures.api.io.packet.PacketManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.CreateNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.MountNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.RemoveNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.UpdateNametagMetadataPacket;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Main-thread packet delivery for nametag lifecycle operations.
 *
 * <p>Scheduling and viewer state live in {@code NametagManager}; this class only emits one coherent
 * packet transaction for the state the manager has already validated.</p>
 */
public final class NametagUpdater {

    public void spawn(Nametag nametag, Player viewer) {
        Player owner = nametag.getNametagOwner();
        if (owner == null || viewer == null || !owner.isOnline() || !viewer.isOnline()) {
            return;
        }

        CreateNametagEntityPacket createPacket = new CreateNametagEntityPacket(
                owner,
                nametag.getEntityId(),
                nametag.getEntityUuid(),
                nametag.snapshotMetadata()
        );
        MountNametagEntityPacket mountPacket = new MountNametagEntityPacket(
                owner,
                passengerList(owner, nametag.getEntityId())
        );

        PacketManager.sendUnicast(
                viewer,
                new BundleDelimiterPacket(),
                createPacket,
                mountPacket,
                new BundleDelimiterPacket()
        );
    }

    public void destroy(int entityId, Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        PacketManager.sendUnicast(viewer, new RemoveNametagEntityPacket(entityId));
    }

    public void destroy(int entityId, Collection<? extends Player> viewers) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        RemoveNametagEntityPacket removePacket = new RemoveNametagEntityPacket(entityId);
        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                PacketManager.sendUnicast(viewer, removePacket);
            }
        }
    }

    public void remount(Nametag nametag, Player viewer) {
        Player owner = nametag.getNametagOwner();
        if (owner == null || viewer == null || !owner.isOnline() || !viewer.isOnline()) {
            return;
        }
        PacketManager.sendUnicast(
                viewer,
                new MountNametagEntityPacket(owner, passengerList(owner, nametag.getEntityId()))
        );
    }

    public void updateMetadata(Nametag nametag, Collection<? extends Player> viewers) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        UpdateNametagMetadataPacket packet = new UpdateNametagMetadataPacket(
                nametag.getEntityId(),
                nametag.snapshotMetadata()
        );
        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                PacketManager.sendUnicast(viewer, packet);
            }
        }
    }

    private int[] passengerList(Player owner, int nametagEntityId) {
        List<Integer> passengerIds = new ArrayList<>();
        for (Entity passenger : owner.getPassengers()) {
            passengerIds.add(passenger.getEntityId());
        }
        passengerIds.add(nametagEntityId);
        return passengerIds.stream().mapToInt(Integer::intValue).toArray();
    }
}
