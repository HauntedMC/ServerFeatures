package nl.hauntedmc.serverfeatures.features.combattag.listener;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.api.combat.CombatUntagReason;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.event.CombatTagAppliedEvent;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver.ResolvedCombatSource;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
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
    private final FeatureTaskManager taskManager;
    private final Set<UUID> pendingKicks = new HashSet<>();
    private final Set<UUID> explodingCreepers = new HashSet<>();

    public CombatTagListener(
            CombatTagSettings settings,
            CombatTagService service,
            CombatSourceResolver sourceResolver,
            FeatureTaskManager taskManager
    ) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.sourceResolver = java.util.Objects.requireNonNull(sourceResolver, "sourceResolver");
        this.taskManager = java.util.Objects.requireNonNull(taskManager, "taskManager");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(event.getFinalDamage() > 0.0D)) {
            return;
        }
        Optional<ResolvedCombatSource> source = sourceResolver.resolve(event);
        if (source.isEmpty()) {
            return;
        }
        ResolvedCombatSource resolved = source.get();
        if (explodingCreepers.contains(resolved.damageSourceId())) {
            return;
        }
        if (!resolved.playerSource()
                && resolved.spawnReason() != null
                && settings.attribution().mobSpawnExclusions().contains(resolved.spawnReason())) {
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
                CombatTagReason.FISHING_HOOK,
                null,
                fisher
        );
        applyCombat(source, target);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        service.handlePlayerDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            service.handleOpponentDeath(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        UUID creeperId = creeper.getUniqueId();
        explodingCreepers.add(creeperId);
        service.handleOpponentDeath(creeper);
        taskManager.scheduleDelayedTask(
                () -> explodingCreepers.remove(creeperId),
                BukkitTime.ticks(20L)
        );
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        pendingKicks.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        pendingKicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        service.handleQuit(player, pendingKicks.remove(player.getUniqueId()));
    }

    public void clear() {
        pendingKicks.clear();
        explodingCreepers.clear();
    }

    private void applyCombat(ResolvedCombatSource source, LivingEntity target) {
        if (target instanceof Player targetPlayer) {
            boolean enabled = source.playerSource()
                    ? settings.tagging().pvpEnabled()
                    : settings.tagging().mobsEnabled();
            if (!enabled
                    || (!source.playerSource()
                    && !isEnemyCombatant(source.sourceEntity(), targetPlayer))) {
                return;
            }
            publishAppliedTag(
                    targetPlayer,
                    service.tagIncoming(
                            targetPlayer,
                            source.opponent(),
                            source.damageSourceId(),
                            source.reason()
                    )
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
            publishAppliedTag(
                    sourcePlayer,
                    service.tagOutgoing(
                            sourcePlayer,
                            CombatSourceResolver.opponent(targetPlayer),
                            targetPlayer.getUniqueId(),
                            source.reason()
                    )
            );
            return;
        }

        if (settings.tagging().mobsEnabled() && isEnemyCombatant(target, sourcePlayer)) {
            publishAppliedTag(
                    sourcePlayer,
                    service.tagOutgoing(
                            sourcePlayer,
                            CombatSourceResolver.opponent(target),
                            target.getUniqueId(),
                            source.reason()
                    )
            );
        }
    }

    private static boolean isEnemyCombatant(Entity entity, Player opposingPlayer) {
        if (entity instanceof Enemy) {
            return true;
        }
        return entity instanceof Mob mob && opposingPlayer.equals(mob.getTarget());
    }

    private static void publishAppliedTag(Player player, CombatTagResult result) {
        if (result != CombatTagResult.TAGGED && result != CombatTagResult.RETAGGED) {
            return;
        }
        player.getServer().getPluginManager().callEvent(new CombatTagAppliedEvent(player, result));
    }

    private static boolean isPortalCause(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL;
    }
}
