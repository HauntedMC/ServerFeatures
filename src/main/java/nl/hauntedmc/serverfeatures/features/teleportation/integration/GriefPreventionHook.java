package nl.hauntedmc.serverfeatures.features.teleportation.integration;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class GriefPreventionHook {

    private final GriefPrevention gp;

    public GriefPreventionHook() {
        Plugin p = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        this.gp = (p instanceof GriefPrevention) ? (GriefPrevention) p : null;
    }

    /** @return true if location is inside a claim (unsuitable for randomtp) */
    public boolean isInClaim(Location loc) {
        if (gp == null) return false;
        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
            return claim != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
