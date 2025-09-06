package nl.hauntedmc.serverfeatures.features.teleportation.integration;

import com.earth2me.essentials.Essentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EssentialsHook {

    private final Essentials essentials;

    public EssentialsHook() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
        this.essentials = (p instanceof Essentials) ? (Essentials) p : null;
    }

    public void setLastLocationIfAvailable(Player player) {
        if (essentials == null) return;
        try {
            essentials.getUser(player).setLastLocation();
        } catch (Throwable ignored) {}
    }
}
