package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.serverfeatures.api.io.packet.PacketManager;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.wrapper.RemoveNametagEntityPacket;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.NametagUpdater;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NametagManager {

    private final NametagRegistry registry;
    private final NametagUpdater updater;
    private final Nametags feature;

    private final VisibilityManager visibilityManager;
    private final int updateIntervalTicks;
    private final int viewerUpdateDelayTicks;

    private final boolean remountFixEnabled;
    private final int remountIntervalTicks;
    private final int debounceUpdateTicks;

    private final Map<UUID, Long> lastUpdateNanos = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> selfViewPreference = new ConcurrentHashMap<>();
    private final Set<UUID> glideSuppressed = ConcurrentHashMap.newKeySet();

    public NametagManager(Nametags feature) {
        this.feature = feature;
        this.registry = new NametagRegistry();
        this.visibilityManager = new VisibilityManager(feature);
        this.updateIntervalTicks = (int) feature.getConfigHandler().getSetting("update_interval_ticks");
        this.viewerUpdateDelayTicks = (int) feature.getConfigHandler().getSetting("viewer_update_delay_ticks");
        this.remountFixEnabled = (boolean) feature.getConfigHandler().getSetting("remount_fix.enabled");
        this.remountIntervalTicks = (int) feature.getConfigHandler().getSetting("remount_fix.interval_ticks");
        this.debounceUpdateTicks = (int) feature.getConfigHandler().getSetting("debounce_update_ticks");

        this.updater = new NametagUpdater(this, feature.getLifecycleManager().getTaskManager());

        scheduleRepeatingUpdate();
        schedulePeriodicRemount();
    }

    public boolean isSelfViewEnabled(UUID playerId) {
        return selfViewPreference.getOrDefault(playerId, true);
    }

    public boolean isSelfViewEnabled(Player player) {
        return isSelfViewEnabled(player.getUniqueId());
    }

    public void setSelfViewEnabled(UUID playerId, boolean enabled) {
        selfViewPreference.put(playerId, enabled);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            updateNametag(p, new UpdateProperties.Builder().build());
        }
    }

    public void setSelfViewEnabled(Player player, boolean enabled) {
        setSelfViewEnabled(player.getUniqueId(), enabled);
    }

    public boolean isSelfViewAllowedNow(UUID playerId) {
        return selfViewPreference.getOrDefault(playerId, true) && !glideSuppressed.contains(playerId);
    }

    public boolean isSelfViewAllowedNow(Player p) {
        return isSelfViewAllowedNow(p.getUniqueId());
    }

    public void setGlideSuppressed(Player p, boolean suppressed) {
        UUID id = p.getUniqueId();
        if (suppressed) glideSuppressed.add(id); else glideSuppressed.remove(id);
        updateNametag(p, new UpdateProperties.Builder().build());
    }

    private void scheduleRepeatingUpdate() {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(() -> {
            for (Nametag nametag : registry.getAllNametags()) {
                updater.update(nametag, new UpdateProperties.Builder().build());
            }
        }, BukkitTime.ticks(0L), BukkitTime.ticks(updateIntervalTicks));
    }

    private void schedulePeriodicRemount() {
        if (!remountFixEnabled || remountIntervalTicks <= 0) return;

        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(() -> {
            for (Nametag nametag : registry.getAllNametags()) {
                if (nametag.getNametagOwner() == null || !nametag.getNametagOwner().isOnline()) continue;

                List<Player> stillVisible = new ArrayList<>();
                for (Player viewer : nametag.getViewers()) {
                    if (viewer != null && viewer.isOnline() && visibilityManager.isPlayerVisible(viewer, nametag)) {
                        stillVisible.add(viewer);
                    }
                }
                if (!stillVisible.isEmpty()) {
                    updater.remount(nametag, stillVisible);
                }
            }
        }, BukkitTime.ticks(remountIntervalTicks), BukkitTime.ticks(remountIntervalTicks));
    }

    private void createNametag(Player player) {
        Nametag nametag = new Nametag(player);
        registry.register(nametag);
        updater.update(nametag, new UpdateProperties.Builder().build());
    }

    private boolean shouldDebounce(UUID playerId) {
        long now = System.nanoTime();
        long minDeltaNanos = Math.max(0, debounceUpdateTicks) * 50_000_000L;
        Long last = lastUpdateNanos.get(playerId);
        if (last != null && (now - last) < minDeltaNanos) {
            return true;
        }
        lastUpdateNanos.put(playerId, now);
        return false;
    }

    public void updateNametag(Player player, UpdateProperties updateProperties) {
        if (player == null) return;

        if (shouldDebounce(player.getUniqueId())) {
            return;
        }

        Optional<Nametag> optTag = registry.getNametag(player.getUniqueId());
        if (optTag.isEmpty()) {
            createNametag(player);
            return;
        }
        Nametag nametag = optTag.get();

        updater.update(nametag, updateProperties);
    }

    public void updateNametag(int entityID, UpdateProperties updateProperties) {
        Optional<Nametag> optTag = registry.getNametagByEntityId(entityID);

        if (optTag.isEmpty()) {
            return;
        }
        Nametag nametag = optTag.get();
        updater.update(nametag, updateProperties);
    }

    public void removeNametag(Player player) {
        Optional<Nametag> optTag = registry.getNametag(player.getUniqueId());
        if (optTag.isPresent()) {
            Nametag nametag = optTag.get();
            registry.unregister(player.getUniqueId());
            selfViewPreference.remove(player.getUniqueId());
            glideSuppressed.remove(player.getUniqueId());
            RemoveNametagEntityPacket removePacket = new RemoveNametagEntityPacket(nametag.getEntityId());
            PacketManager.sendMulticast(new ArrayList<>(nametag.getViewers()), removePacket);
        }
    }

    public void removeAllNametags() {
        for (Nametag nametag : registry.getAllNametags()) {
            removeNametag(nametag.getNametagOwner());
        }
        selfViewPreference.clear();
        glideSuppressed.clear();
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            createNametag(player);
        }
    }

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

    public VisibilityManager getVisibilityManager() {
        return visibilityManager;
    }

    public int getViewerUpdateDelayTicks() {
        return viewerUpdateDelayTicks;
    }
}
