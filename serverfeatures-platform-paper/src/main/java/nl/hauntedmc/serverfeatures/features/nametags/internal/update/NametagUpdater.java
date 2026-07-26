package nl.hauntedmc.serverfeatures.features.nametags.internal.update;

import nl.hauntedmc.serverfeatures.api.io.packet.BundleDelimiterPacket;
import nl.hauntedmc.serverfeatures.api.io.packet.PacketManager;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.internal.Nametag;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.CreateNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.MountNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.RemoveNametagEntityPacket;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NametagUpdater {
    private final FeatureTaskManager taskManager;
    private final NametagManager nametagManager;

    public NametagUpdater(NametagManager nametagManager, FeatureTaskManager taskManager) {
        this.nametagManager = nametagManager;
        this.taskManager = taskManager;
    }

    public void update(Nametag nametag, UpdateProperties updateProperties) {
        if (updateProperties.getUpdateText()) {
            taskManager.scheduleDelayedTask(nametag::updateNametagText, BukkitTime.ticks(updateProperties.getDelay()));
        }

        if (updateProperties.isOwnerOnly()) {
            ownerOnlyUpdate(nametag, updateProperties.getDelay());
            return;
        }

        if (updateProperties.isForced()) {
            forceUpdate(nametag, updateProperties.getDelay());
            return;
        }

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
            if (viewer == null || !viewer.isOnline()) continue;

            if (viewer.getUniqueId().equals(nametag.getNametagOwnerId())
                    && !nametagManager.isSelfViewAllowedNow(viewer)) {
                continue;
            }

            if (nametagManager.getVisibilityManager().isPlayerVisible(viewer, nametag)) {
                newViewers.add(viewer);
            }
        }

        List<Player> viewersToAdd = new ArrayList<>(newViewers);
        viewersToAdd.removeAll(nametag.getViewers());

        List<Player> viewersToRemove = new ArrayList<>(nametag.getViewers());
        viewersToRemove.removeAll(newViewers);

        if (!viewersToAdd.isEmpty()) {
            createNametagEntity(nametag, viewersToAdd, delay);
        }

        if (!viewersToRemove.isEmpty()) {
            removeNametagEntity(nametag, viewersToRemove);
        }

        nametag.getViewers().clear();
        nametag.getViewers().addAll(newViewers);
    }

    private void createNametagEntity(Nametag nametag, List<Player> viewersToAdd, long delay) {
        CreateNametagEntityPacket createPacket = new CreateNametagEntityPacket(
                nametag.getNametagOwner(),
                nametag.getEntityId(),
                nametag.getNametagProperties().getMetadata()
        );
        int[] passengerList = updatePassengerList(nametag.getNametagOwner(), nametag);
        MountNametagEntityPacket mountPacket = new MountNametagEntityPacket(nametag.getNametagOwner(), passengerList);
        taskManager.scheduleDelayedTask(() -> {
            PacketManager.sendMulticast(viewersToAdd, new BundleDelimiterPacket());
            PacketManager.sendMulticast(viewersToAdd, createPacket);
            PacketManager.sendMulticast(viewersToAdd, mountPacket);
            PacketManager.sendMulticast(viewersToAdd, new BundleDelimiterPacket());
        }, BukkitTime.ticks(delay + nametagManager.getViewerUpdateDelayTicks()));
    }

    private void removeNametagEntity(Nametag nametag, List<Player> viewers) {
        RemoveNametagEntityPacket removePacket = new RemoveNametagEntityPacket(nametag.getEntityId());
        PacketManager.sendMulticast(viewers, removePacket);
    }

    public void remount(Nametag nametag, List<Player> viewers) {
        if (viewers == null || viewers.isEmpty()) return;
        int[] passengerList = updatePassengerList(nametag.getNametagOwner(), nametag);
        MountNametagEntityPacket mountPacket = new MountNametagEntityPacket(nametag.getNametagOwner(), passengerList);
        PacketManager.sendMulticast(viewers, mountPacket);
    }

    private int[] updatePassengerList(Player player, Nametag nametag) {
        List<Integer> passengerIds = new ArrayList<>();

        passengerIds.add(nametag.getEntityId());
        for (Entity passenger : player.getPassengers()) {
            passengerIds.add(passenger.getEntityId());
        }

        return passengerIds.stream().mapToInt(Integer::intValue).toArray();
    }
}
