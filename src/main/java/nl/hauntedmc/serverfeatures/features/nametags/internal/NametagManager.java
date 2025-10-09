package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.api.io.packet.PacketManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.RemoveNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.NametagUpdater;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.PassengerHandler;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The API for creating, updating, removing, and refreshing nametags.
 * This manager delegates packet sending and viewer logic to the NametagUpdater.
 */
public class NametagManager {

    private final NametagRegistry registry;
    private final NametagUpdater updater;
    private final Nametags feature;

    private final PassengerHandler passengerHandler;

    private final VisibilityManager visibilityManager;
    private final int updateIntervalTicks;
    private final int viewerUpdateDelayTicks;

    public NametagManager(Nametags feature) {
        this.feature = feature;
        this.registry = new NametagRegistry();
        this.passengerHandler = new PassengerHandler();
        this.visibilityManager = new VisibilityManager(feature);
        this.updateIntervalTicks = (int) feature.getConfigHandler().getSetting("update_interval_ticks");
        this.viewerUpdateDelayTicks = (int) feature.getConfigHandler().getSetting("viewer_update_delay_ticks");

        this.updater = new NametagUpdater(this, feature.getLifecycleManager().getTaskManager());
        scheduleRepeatingUpdate();
    }

    /**
     * Schedules a repeating task that forces updates on all active nametags.
     */
    private void scheduleRepeatingUpdate() {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(() -> {
            for (Nametag nametag : registry.getAllNametags()) {
                updater.update(nametag, new UpdateProperties.Builder().build());
            }
        }, BukkitTime.ticks(0L), BukkitTime.ticks(updateIntervalTicks));
    }

    /**
     * Creates a nametag for the given player.
     */
    private void createNametag(Player player) {
        Nametag nametag = new Nametag(player);
        registry.register(nametag);
        updater.update(nametag, new UpdateProperties.Builder().build());
    }

    /**
     * Updates an existing nametag (or creates one if it does not exist).
     */
    public void updateNametag(Player player, UpdateProperties updateProperties) {
        Optional<Nametag> optTag = registry.getNametag(player.getUniqueId());
        if (optTag.isEmpty()) {
            createNametag(player);
            return;
        }
        Nametag nametag = optTag.get();

        updater.update(nametag, updateProperties);
    }

    /**
     * Updates an existing nametag (or creates one if it does not exist).
     */
    public void updateNametag(int entityID, UpdateProperties updateProperties) {
        Optional<Nametag> optTag = registry.getNametagByEntityId(entityID);

        if (optTag.isEmpty()) {
            return;
        }
        Nametag nametag = optTag.get();
        updater.update(nametag, updateProperties);
    }

    /**
     * Removes a player's nametag.
     */
    public void removeNametag(Player player) {
        Optional<Nametag> optTag = registry.getNametag(player.getUniqueId());
        if (optTag.isPresent()) {
            Nametag nametag = optTag.get();
            registry.unregister(player.getUniqueId());
            RemoveNametagEntityPacket removePacket = new RemoveNametagEntityPacket(nametag.getEntityId());
            PacketManager.sendMulticast(new ArrayList<>(nametag.getViewers()), removePacket);
        }
    }

    public void removeAllNametags() {
        for (Nametag nametag : registry.getAllNametags()) {
            removeNametag(nametag.getNametagOwner());
        }
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            createNametag(player);
        }
    }

    /**
     * Gets a list of all players who have a registered nametag.
     *
     * @return List of players with registered nametags.
     */
    public List<Player> getRegisteredPlayers() {
        List<Player> players = new ArrayList<>();
        for (Nametag nametag : registry.getAllNametags()) {
            Player player = nametag.getNametagOwner();
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public PassengerHandler getPassengerHandler() {
        return passengerHandler;
    }

    public VisibilityManager getVisibilityManager() {
        return visibilityManager;
    }

    public int getViewerUpdateDelayTicks() {
        return viewerUpdateDelayTicks;
    }
}

