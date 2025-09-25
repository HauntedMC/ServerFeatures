package nl.hauntedmc.serverfeatures.features.nametags.internal.visibility;

import nl.hauntedmc.serverfeatures.api.APIRegistry;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Decides whether the nametag should be used for a given player.
 * Blocks usage when the player is vanished, spectator, invisible, or disguised.
 *
 * - Vanish check uses your APIRegistry/VanishAPI.
 * - Disguise check is reflective (no hard compile-time dep).
 * - No per-viewer logic; this is owner-scoped.
 */
public final class NametagUsageGate {

    /** @return true if we SHOULD create/use a nametag for this player; false to remove/avoid it. */
    public boolean allowNametag(Player p) {
        if (p == null || !p.isOnline()) return false;
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        if (isVanished(p)) return false;
        if (isInvisible(p)) return false;
        if (isDisguised(p)) return false;
        return true;
    }

    private boolean isVanished(Player p) {
        // Your requested vanish check
        return APIRegistry.get(VanishAPI.class)
                .map(api -> api.isVanished(p.getUniqueId()))
                .orElse(false);
    }

    private boolean isInvisible(Player p) {
        // Covers server-side invisibility and potion effect
        if (p.isInvisible()) return true;
        return p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private boolean isDisguised(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return false;
        try {
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            Object r = api.getMethod("isDisguised", Player.class).invoke(null, p);
            return (r instanceof Boolean) && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
