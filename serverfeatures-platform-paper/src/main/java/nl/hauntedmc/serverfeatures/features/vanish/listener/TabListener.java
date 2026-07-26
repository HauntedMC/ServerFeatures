package nl.hauntedmc.serverfeatures.features.vanish.listener;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filters tab-completion suggestions so vanished player names don't leak
 * to players who are not allowed to see them.
 */
public class TabListener implements Listener {

    private final Vanish feature;

    public TabListener(Vanish feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTab(TabCompleteEvent e) {
        if (!(boolean) feature.getConfigHandler().get("filter_tab_completion")) return;
        if (!(e.getSender() instanceof Player sender)) return;
        if (sender.hasPermission(VanishService.PERM_SEE)) return;

        List<String> filtered = e.getCompletions().stream()
                .filter(this::notVanishedName)
                .collect(Collectors.toList());
        e.setCompletions(filtered);
    }

    private boolean notVanishedName(String suggestion) {
        Player p = Bukkit.getPlayerExact(suggestion);
        if (p == null) return true; // not a player name
        UUID id = p.getUniqueId();
        return !feature.getService().isVanished(id);
    }
}
