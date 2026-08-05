package nl.hauntedmc.serverfeatures.features.combattag.listener;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatUntagReason;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver.ResolvedCombatSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CombatTagListener implements Listener {

    private final CombatTagSettings settings;
    private final CombatTagService service;
    private final CombatSourceResolver sourceResolver;
    private final Set<UUID> spawnExcludedMobs = new HashSet<>();

    public CombatTagListener(
            CombatTagSettings settings,
            CombatTagService service,
            CombatSourceResolver sourceResolver
    ) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.sourceResolver = java.util.Objects.requireNonNull(sourceResolver, "sourceResolver");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Optional<ResolvedCombatSource> source = sourceResolver.resolve(event.getDamager());
        if (source.isEmpty()) {
            return;
        }
        ResolvedCombatSource resolved = source.get();
        if (!resolved.playerSource() && spawnExcludedMobs.contains(resolved.damageSourceId())) {
            return;
        }
        applyCombat(resolved, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishingHook(PlayerFishEvent event) {
        if (!settings.attribution().linkFishingHooks()
                || event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                || !(event.getCaught() instanceof LivingEntity target)) {
            return;
        }
        Player fisher = event.getPlayer();
        ResolvedCombatSource source = new ResolvedCombatSource(
                CombatSourceResolver.opponent(fisher),
                fisher.getUniqueId(),
                fisher,
                CombatTagReason.FISHING_HOOK
        );
        applyCombat(source, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (settings.attribution().mobSpawnExclusions().contains(event.getSpawnReason())) {
            spawnExcludedMobs.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        spawnExcludedMobs.remove(event.getPlayer().getUniqueId());
        service.handlePlayerDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        spawnExcludedMobs.remove(entity.getUniqueId());
        if (!(entity instanceof Player)) {
            service.handleOpponentDeath(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof Creeper) {
            service.handleOpponentDeath(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!service.isTagged(player)) {
            return;
        }

        boolean portal = event instanceof PlayerPortalEvent || isPortalCause(event.getCause());
        if (portal && settings.teleport().preventPortals()) {
            event.setCancelled(true);
            service.sendTeleportBlocked(player, true);
            return;
        }

        boolean allowed = settings.teleport().allowedCauses().contains(event.getCause());
        if (!portal && settings.teleport().preventOtherTeleports() && !allowed) {
            event.setCancelled(true);
            service.sendTeleportBlocked(player, false);
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && settings.teleport().enderPearlResetsTimer()) {
            service.resetTimer(player);
        }
        if (settings.teleport().clearAfterAllowedTeleport()) {
            service.untag(player, CombatUntagReason.TELEPORT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.handleWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }

    public void pruneSpawnExclusions() {
        spawnExcludedMobs.removeIf(entityId -> Bukkit.getEntity(entityId) == null);
    }

    public void clear() {
        spawnExcludedMobs.clear();
    }

    private void applyCombat(ResolvedCombatSource source, LivingEntity target) {
        if (target instanceof Player targetPlayer) {
            boolean enabled = source.playerSource()
                    ? settings.tagging().pvpEnabled()
                    : settings.tagging().mobsEnabled();
            if (!enabled) {
                return;
            }
            service.tag(
                    targetPlayer,
                    source.opponent(),
                    source.damageSourceId(),
                    source.reason()
            );
        }

        Player sourcePlayer = source.player();
        if (sourcePlayer == null) {
            return;
        }

        if (target instanceof Player targetPlayer) {
            if (!settings.tagging().pvpEnabled()) {
                return;
            }
            service.tag(
                    sourcePlayer,
                    CombatSourceResolver.opponent(targetPlayer),
                    targetPlayer.getUniqueId(),
                    source.reason()
            );
            return;
        }

        if (settings.tagging().mobsEnabled()) {
            service.tag(
                    sourcePlayer,
                    CombatSourceResolver.opponent(target),
                    target.getUniqueId(),
                    source.reason()
            );
        }
    }

    private static boolean isPortalCause(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL;
    }
}
