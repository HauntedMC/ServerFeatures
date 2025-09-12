package nl.hauntedmc.serverfeatures.features.tablist.internal;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

final class VaultRankResolver implements RankResolver {

    private final Permission permission;

    VaultRankResolver() {
        RegisteredServiceProvider<Permission> rsp =
                Bukkit.getServicesManager().getRegistration(Permission.class);
        this.permission = (rsp != null) ? rsp.getProvider() : null;
    }

    @Override
    public boolean isReady() {
        return permission != null;
    }

    @Override
    public String getRank(Player player) {
        if (permission == null) return "default";
        String group = permission.getPrimaryGroup(player);
        return (group != null && !group.isEmpty()) ? group.toLowerCase() : "default";
    }
}
