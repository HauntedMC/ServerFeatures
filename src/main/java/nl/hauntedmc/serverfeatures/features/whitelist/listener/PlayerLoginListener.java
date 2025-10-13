package nl.hauntedmc.serverfeatures.features.whitelist.listener;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.whitelist.Whitelist;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

/**
 * Disallow non-whitelisted players during the async pre-login phase.
 * This avoids the client hanging on "Reconfiguring" (which can happen if you kick on Join).
 * Bypass logic:
 * - If LuckPerms is present, we resolve the user's permission data and check
 * "serverfeatures.feature.whitelist.bypass" asynchronously.
 * - Otherwise we fall back to checking if the player is OP (customize as needed).
 */
public class PlayerLoginListener implements Listener {

    private static final String BYPASS_NODE = "serverfeatures.feature.whitelist.bypass";
    private final Whitelist feature;

    public PlayerLoginListener(Whitelist feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        final UUID uuid = event.getUniqueId();

        // Resolve bypass: LuckPerms (if present) -> OP fallback
        boolean bypass = hasLuckPermsBypass(uuid);
        if (!bypass) {
            // Optional fallback: treat ops as bypass (adjust/remove if undesired)
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            bypass = off.isOp();
        }

        if (bypass) return;

        // Build kick message using your localization
        Component kick = feature.getLocalizationHandler()
                .getMessage("whitelist.kick_message")
                .build();

        // Cleanly disallow at pre-login stage
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kick);
    }

    /**
     * If LuckPerms is available, resolve the user's cached permission data asynchronously
     * and check the bypass node. Safe to block here because this event is already async.
     */
    private boolean hasLuckPermsBypass(UUID uuid) {
        try {
            // Only proceed if LuckPerms is actually loaded
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return false;

            // Avoid class loading errors if LP is absent at runtime
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();

            // Load (or get) the user asynchronously and wait (we are already async)
            var user = lp.getUserManager().loadUser(uuid).join();
            if (user == null) return false;

            // Get contextual permission data
            var queryOptions = lp.getContextManager().getStaticQueryOptions();
            var data = user.getCachedData().getPermissionData(queryOptions);

            return data.checkPermission(PlayerLoginListener.BYPASS_NODE).asBoolean();
        } catch (Throwable t) {
            // Any issue (no LP on classpath, error fetching user, etc.) => treat as no bypass
            return false;
        }
    }
}
