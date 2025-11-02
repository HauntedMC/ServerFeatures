package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Small helper for FX, configurable via "play_sounds".
 */
public class TeleportEffects {

    private final Teleportation feature;

    public TeleportEffects(Teleportation feature) {
        this.feature = feature;
    }

    private boolean playSoundsEnabled() {
        Object v = feature.getConfigHandler().get("play_sounds");
        return (v instanceof Boolean b) ? b : true;
    }

    public void playFor(Player p) {
        if (!playSoundsEnabled()) return;
        try {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 10f, 1.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 10f, 1.5f);
        } catch (Throwable ignored) {
        }
    }
}
