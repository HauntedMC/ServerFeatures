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
import java.util.logging.Logger;

/**
 * Main-thread packet delivery for nametag lifecycle operations.
 *
 * <p>Scheduling and viewer state live in {@code NametagManager}; this class only emits one coherent
 * packet transaction for the state the manager has already validated.</p>
 */
public final class NametagUpdater {
    private static final Logger LOGGER = Logger.getLogger("ServerFeatures-Nametags");

    public void spawn(Nametag nametag, Player viewer) {
        Player owner = nametag.getNametagOwner();
        if (owner == null || viewer == null || !owner.isOnline() || !viewer.isOnline()) {
            throw new IllegalStateException("Cannot spawn a nametag for an offline owner or viewer.");
        }

        CreateNametagEntityPacket createPacket = new CreateNametagEntityPacket(
                owner,
                nametag.getEntityId(),
                nametag.getEntityUuid()
        );
        MountNametagEntityPacket mountPacket = new MountNametagEntityPacket(
                owner,
                passengerList(owner, nametag.getEntityId())
        );
        UpdateNametagMetadataPacket metadataPacket = new UpdateNametagMetadataPacket(
                nametag.getEntityId(),
                nametag.snapshotMetadata()
        );

        RuntimeException failure = null;
        boolean bundleOpened = false;
        try {
            PacketManager.sendUnicast(viewer, new BundleDelimiterPacket());
            bundleOpened = true;
            PacketManager.sendUnicast(viewer, createPacket, mountPacket, metadataPacket);
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            if (bundleOpened) {
                try {
                    PacketManager.sendUnicast(viewer, new BundleDelimiterPacket());
                } catch (RuntimeException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
        }

        if (failure != null) {
            safeDestroy(nametag.getEntityId(), viewer, "partial spawn cleanup");
            throw failure;
        }
    }

    public void destroy(int entityId, Player viewer) {
        safeDestroy(entityId, viewer, "destroy");
    }

    public void destroy(int entityId, Collection<? extends Player> viewers) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (Player viewer : viewers) {
            safeDestroy(entityId, viewer, "multicast destroy");
        }
    }

    public void remount(Nametag nametag, Player viewer) {
        Player owner = nametag.getNametagOwner();
        if (owner == null || viewer == null || !owner.isOnline() || !viewer.isOnline()) {
            return;
        }
        try {
            PacketManager.sendUnicast(
                    viewer,
                    new MountNametagEntityPacket(owner, passengerList(owner, nametag.getEntityId()))
            );
        } catch (RuntimeException exception) {
            logPacketFailure("remount", nametag.getEntityId(), viewer, exception);
        }
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
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            try {
                PacketManager.sendUnicast(viewer, packet);
            } catch (RuntimeException exception) {
                logPacketFailure("metadata update", nametag.getEntityId(), viewer, exception);
            }
        }
    }

    private void safeDestroy(int entityId, Player viewer, String operation) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        RuntimeException firstFailure;
        try {
            PacketManager.sendUnicast(viewer, new RemoveNametagEntityPacket(entityId));
            return;
        } catch (RuntimeException exception) {
            firstFailure = exception;
        }

        try {
            PacketManager.sendUnicast(viewer, new RemoveNametagEntityPacket(entityId));
        } catch (RuntimeException retryFailure) {
            firstFailure.addSuppressed(retryFailure);
            logPacketFailure(operation, entityId, viewer, firstFailure);
        }
    }

    private void logPacketFailure(String operation, int entityId, Player viewer, RuntimeException exception) {
        LOGGER.warning(
                "[ServerFeatures] [Nametags] Failed to " + operation + " entity " + entityId
                        + " for " + viewer.getName() + ": " + rootMessage(exception)
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
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
