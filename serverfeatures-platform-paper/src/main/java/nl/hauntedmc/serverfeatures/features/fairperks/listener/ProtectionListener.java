package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.util.DamageSourceResolver;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class ProtectionListener implements Listener {

    private final FairPerks feature;

    public ProtectionListener(FairPerks feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && feature.stateService().consumeFallDamageGrace(player)) {
            event.setCancelled(true);
            player.setFallDistance(0.0F);
            return;
        }
        if (!feature.stateService().isGodEffective(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                && !feature.settings().god().protectVoid()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPerkDamage(EntityDamageByEntityEvent event) {
        Player attacker = DamageSourceResolver.resolvePlayer(event.getDamager());
        if (attacker == null
                || attacker.hasPermission(FairPerks.RESTRICTION_BYPASS_PERMISSION)
                || !feature.stateService().isRestricted(attacker)) {
            return;
        }

        if (event.getEntity() instanceof Player target
                && !target.getUniqueId().equals(attacker.getUniqueId())
                && feature.settings().restrictions().pvp()) {
            event.setCancelled(true);
            feature.sendActionBar(
                    attacker,
                    "fairperks.restriction.pvp."
                            + feature.stateService().activeRestrictionMessageSuffix(attacker)
            );
            return;
        }

        if (!feature.hostileClassifier().isHostile(event.getEntity())
                || feature.hostileClassifier().isExemptSpawnerMob(event.getEntity())) {
            return;
        }
        boolean directMelee = event.getDamager() instanceof Player;
        boolean blocked = directMelee
                ? feature.settings().restrictions().hostileMelee()
                : feature.settings().restrictions().hostileProjectiles();
        if (!blocked) {
            return;
        }
        event.setCancelled(true);
        feature.sendActionBar(
                attacker,
                "fairperks.restriction.hostile."
                        + feature.stateService().activeRestrictionMessageSuffix(attacker)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileTarget(EntityTargetLivingEntityEvent event) {
        if (!feature.settings().restrictions().hostileTargeting()
                || !(event.getTarget() instanceof Player player)
                || player.hasPermission(FairPerks.RESTRICTION_BYPASS_PERMISSION)
                || !feature.hostileClassifier().isHostile(event.getEntity())
                || !feature.stateService().isRestricted(player)) {
            return;
        }
        event.setTarget(null);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!event.hasChangedBlock()
                || !((Entity) player).isOnGround()
                || !feature.stateService().view(player).fallDamageGrace()) {
            return;
        }

        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            if (player.isOnline() && ((Entity) player).isOnGround()) {
                feature.stateService().clearFallDamageGrace(player);
            }
        });
    }
}
