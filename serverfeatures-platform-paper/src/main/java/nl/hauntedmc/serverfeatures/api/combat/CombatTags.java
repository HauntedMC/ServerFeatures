package nl.hauntedmc.serverfeatures.api.combat;

import nl.hauntedmc.serverfeatures.api.ServerFeaturesApi;
import nl.hauntedmc.serverfeatures.api.capability.combat.CombatTagApi;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRef;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Paper-runtime bridge for legacy internal callers while they are migrated to injected capability
 * references. This class is not part of the published API artifact and owns no service instance.
 */
public final class CombatTags {

    private CombatTags() {
    }

    public static CapabilityRef<CombatTagApi> service() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ServerFeatures");
        if (!(plugin instanceof ServerFeaturesApi api)) {
            throw new IllegalStateException("ServerFeatures public API is not available");
        }
        return api.capabilities().reference(CombatTagApi.class);
    }
}
