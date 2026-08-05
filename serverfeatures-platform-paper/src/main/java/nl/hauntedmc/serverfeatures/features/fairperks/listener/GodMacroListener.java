package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GodMacroListener implements Listener {

    private final FairPerks feature;
    private final Map<UUID, Long> lastSneakNanos = new HashMap<>();

    public GodMacroListener(FairPerks feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()
                || !player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)
                || !player.hasPermission(FairPerks.GOD_USE_PERMISSION)
                || !feature.stateService().isGodMacroEnabled(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastSneakNanos.put(playerId, now);
        long intervalNanos = feature.settings().godMacro().intervalMillis() * 1_000_000L;
        if (previous == null || now - previous > intervalNanos) {
            return;
        }
        lastSneakNanos.remove(playerId);

        PerkChangeResult result = feature.stateService().toggle(
                player,
                PerkType.GOD,
                player.hasPermission(FairPerks.GOD_BYPASS_PERMISSION)
        );
        if (result.success()) {
            String state = result.enabled() ? "enabled" : "disabled";
            String key = result.status() == PerkChangeResult.Status.ALREADY_IN_STATE
                    ? "fairperks.god.already_" + state
                    : "fairperks.god." + state;
            feature.sendMessage(player, key);
            return;
        }
        feature.sendMessage(player, switch (result.status()) {
            case NO_PERMISSION -> "fairperks.no_permission";
            case COMBAT_TAGGED -> "fairperks.denied.combat";
            case HOSTILE_NEARBY -> "fairperks.denied.hostile";
            case WORLD_BLOCKED -> "fairperks.denied.world";
            case GAME_MODE_BLOCKED -> "fairperks.denied.game_mode";
            case CHANGED, ALREADY_IN_STATE -> throw new IllegalStateException();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastSneakNanos.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        lastSneakNanos.remove(event.getEntity().getUniqueId());
    }
}
