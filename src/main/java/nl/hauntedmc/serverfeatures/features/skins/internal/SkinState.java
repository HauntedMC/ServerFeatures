package nl.hauntedmc.serverfeatures.features.skins.internal;

import nl.hauntedmc.serverfeatures.features.skins.Skins;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinState {

    private final Skins feature;

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hasCustomSkin = new ConcurrentHashMap<>();

    public SkinState(Skins feature) {
        this.feature = feature;
    }

    /* ----------------------------------------------- */
    /*  State accessors                                */
    /* ----------------------------------------------- */

    public int getCooldownSeconds() {
        Object v = feature.getConfigHandler().getSetting("cooldown_seconds");
        return (v instanceof Number) ? ((Number) v).intValue() : 60;
    }

    public long getLastUse(UUID uuid) {
        return lastUse.getOrDefault(uuid, 0L);
    }

    public void setLastUse(UUID uuid, long epochMillis) {
        lastUse.put(uuid, epochMillis);
    }

    public void clearLastUse(UUID uuid) {
        lastUse.remove(uuid);
    }

    public boolean hasCustomSkin(UUID uuid) {
        return hasCustomSkin.getOrDefault(uuid, false);
    }

    public void markCustomSkin(UUID uuid, boolean applied) {
        if (applied) {
            hasCustomSkin.put(uuid, true);
        } else {
            hasCustomSkin.remove(uuid);
        }
    }

    public void clearAll() {
        lastUse.clear();
        hasCustomSkin.clear();
    }
}
