package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

import nl.hauntedmc.serverfeatures.common.packet.BundleDelimiterPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.CreateNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.MountNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.RemoveNametagEntityPacket;
import nl.hauntedmc.serverfeatures.common.packet.PacketManager;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureTaskManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles sending packets and updating viewer logic for a given Nametag.
 */
public class NametagUpdater {
    private final FeatureTaskManager taskManager;
    private final NametagManager nametagManager;

    public NametagUpdater(NametagManager nametagManager, FeatureTaskManager taskManager) {
        this.nametagManager = nametagManager;
        this.taskManager = taskManager;
    }

    /**
     * Performs a hard update on the given nametag.
     * It compares the current viewers with the ones that should see the nametag,
     * sends the appropriate packets (create, mount, or remove) to update the client,
     * and updates the internal viewers list.
     *
     * @param nametag the nametag to update.
     */
    public void update(Nametag nametag, UpdateProperties updateProperties) {

        // Update the text if flagged
        if (updateProperties.getUpdateText()) {
            taskManager.scheduleDelayedTask(nametag::updateNametagText, updateProperties.getDelay());
        }

        // If owner only update, recreate the complete nametag entity
        if (updateProperties.isOwnerOnly()) {
            ownerOnlyUpdate(nametag, updateProperties.getDelay());
            return;
        }

        // If forced, remove all viewers, remove the entity for all current viewers, and resend to new viewers
        if (updateProperties.isForced()) {
            forceUpdate(nametag, updateProperties.getDelay());
            return;
        }

        // Ordinary update
        updateViewers(nametag, updateProperties.getDelay());
    }

    private void forceUpdate(Nametag nametag, long delay) {
        List<Player> viewers = nametag.getViewers().stream().toList();
        nametag.getViewers().clear();
        removeNametagEntity(nametag, viewers);
        updateViewers(nametag, delay);
    }

    private void ownerOnlyUpdate(Nametag nametag, long delay) {
        List<Player> viewers = List.of(nametag.getNametagOwner());
        removeNametagEntity(nametag, viewers);
        createNametagEntity(nametag, viewers, delay);
    }

    private void updateViewers(Nametag nametag, long delay) {
        List<Player> newViewers = new ArrayList<>();
        for (Player viewer : nametagManager.getRegisteredPlayers()) {
            if (nametagManager.getVisibilityManager().isPlayerVisible(viewer, nametag)) {
                newViewers.add(viewer);
            }
        }

        // Determine viewers to add and remove.
        List<Player> viewersToAdd = new ArrayList<>(newViewers);
        viewersToAdd.removeAll(nametag.getViewers());

        List<Player> viewersToRemove = new ArrayList<>(nametag.getViewers());
        viewersToRemove.removeAll(newViewers);

        // For viewers to add, send create packet and schedule mount packet.
        if (!viewersToAdd.isEmpty()) {
            createNametagEntity(nametag, viewersToAdd, delay);
        }

        // For viewers to remove, send remove packet.
        if (!viewersToRemove.isEmpty()) {
            removeNametagEntity(nametag, viewersToRemove);
        }

        // Update the internal viewers set.
        nametag.getViewers().clear();
        nametag.getViewers().addAll(newViewers);
    }

    private void createNametagEntity(Nametag nametag, List<Player> viewersToAdd, long delay) {
        CreateNametagEntityPacket createPacket = new CreateNametagEntityPacket(
                nametag.getNametagOwner(),
                nametag.getEntityId(),
                nametag.getNametagProperties().getMetadata()
        );
        int[] passengerList = nametagManager.getPassengerHandler().updatePassengerList(nametag.getNametagOwner(), nametag);
        MountNametagEntityPacket mountPacket = new MountNametagEntityPacket(nametag.getNametagOwner(), passengerList);
        taskManager.scheduleDelayedTask(() -> {
            PacketManager.sendMulticast(viewersToAdd, new BundleDelimiterPacket());
            PacketManager.sendMulticast(viewersToAdd, createPacket);
            PacketManager.sendMulticast(viewersToAdd, mountPacket);
            PacketManager.sendMulticast(viewersToAdd, new BundleDelimiterPacket());
        }, delay + nametagManager.getViewerUpdateDelayTicks());
    }

    private void removeNametagEntity(Nametag nametag, List<Player> viewers) {
        RemoveNametagEntityPacket removePacket = new RemoveNametagEntityPacket(nametag.getEntityId());
        PacketManager.sendMulticast(viewers, removePacket);
    }
}
