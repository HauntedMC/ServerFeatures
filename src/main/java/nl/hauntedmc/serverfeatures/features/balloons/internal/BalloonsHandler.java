package nl.hauntedmc.serverfeatures.features.balloons.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.model.BalloonDefinition;
import nl.hauntedmc.serverfeatures.features.balloons.util.Distance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawning, removal and tethering of balloons.
 * Uses a hidden Parrot as a "leash anchor" and a small ArmorStand with the helmet item as the balloon visual.
 * No item mode, no inflate/deflate; always show particles on removal.
 */
public final class BalloonsHandler {

    private static final String TAG = "ServerFeaturesBalloons";
    private static final double LEASH_FOLLOW_MAX = 6.0D;
    private static final double TELEPORT_MAX = 10.0D;
    private static final double ARMORSTAND_OFFSET_Y = -1.3D;

    private final Balloons feature;

    // Runtime state
    private final Map<UUID, BalloonDefinition> active = new ConcurrentHashMap<>();
    private final Map<UUID, Parrot> parrots = new ConcurrentHashMap<>();
    private final Map<UUID, ArmorStand> stands = new ConcurrentHashMap<>();

    public BalloonsHandler(Balloons feature) {
        this.feature = feature;

        // Tether/physics tick (every 2 ticks)
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(this::tetherTick, BukkitTime.ticks(2));
    }

    public Optional<BalloonDefinition> getActiveBalloon(Player p) {
        return Optional.ofNullable(active.get(p.getUniqueId()));
    }

    public boolean setBalloon(Player player, BalloonDefinition def) {
        if (!player.hasPermission("serverfeatures.feature.balloons.use")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(player).build());
            return false;
        }
        if (!player.hasPermission(def.permission())) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission_reason")
                    .with("reason", feature.getLocalizationHandler().getMessage("balloons.menu.balloon.lore.locked").forAudience(player).build())
                    .forAudience(player).build());
            return false;
        }

        ItemStack helmet = def.asHelmetItem();

        UUID u = player.getUniqueId();
        if (parrots.containsKey(u)) {
            // Already active -> just swap helmet
            ArmorStand as = stands.get(u);
            if (as != null && !as.isDead()) {
                as.getEquipment().setHelmet(helmet);
            } else {
                // safety: respawn everything
                removeBalloon(player);
                summon(player, helmet);
            }
        } else {
            summon(player, helmet);
        }
        active.put(u, def);
        return true;
    }

    public boolean removeBalloon(Player player) {
        UUID u = player.getUniqueId();
        if (!parrots.containsKey(u)) return false;

        ArmorStand as = stands.remove(u);
        if (as != null && !as.isDead()) {
            as.getWorld().spawnParticle(Particle.CLOUD, as.getLocation().add(0, 2, 0), 5, 0.1, 0.1, 0.1, 0.1);
            as.remove();
        }

        Parrot parrot = parrots.remove(u);
        if (parrot != null && !parrot.isDead()) parrot.remove();

        active.remove(u);
        return true;
    }

    private void summon(Player player, ItemStack helmet) {
        UUID u = player.getUniqueId();

        // Parrot anchor (leash)
        Location loc = player.getLocation().add(0, 2, 0);
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(loc, EntityType.PARROT);
        parrot.setInvisible(true);
        parrot.setSilent(true);
        parrot.setInvulnerable(true);
        parrot.addScoreboardTag(TAG);
        parrot.setLeashHolder(player);
        parrots.put(u, parrot);

        // ArmorStand visual
        ArmorStand as = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setCanPickupItems(false);
        as.setArms(false);
        as.setBasePlate(false);
        as.addScoreboardTag(TAG);
        as.getEquipment().setHelmet(helmet);
        lockAll(as);
        stands.put(u, as);
    }

    private void tetherTick() {
        for (UUID u : new ArrayList<>(parrots.keySet())) {
            Parrot parrot = parrots.get(u);
            ArmorStand as = stands.get(u);
            if (parrot == null || as == null) continue;

            Player player = feature.getPlugin().getServer().getPlayer(u);
            if (player == null || !player.isOnline()) {
                removeBalloonInternal(u);
                continue;
            }

            if (parrot.getWorld() != player.getWorld()) {
                parrot.teleport(player);
            }

            double dist = parrot.getLocation().distance(player.getLocation());
            if (dist > TELEPORT_MAX) {
                parrot.teleport(player);
            } else if (dist < LEASH_FOLLOW_MAX && parrot.isLeashed()) {
                Distance.line(parrot, player);
            }

            Location standLoc = parrot.getLocation().clone().add(0, ARMORSTAND_OFFSET_Y, 0);
            as.teleport(standLoc);
        }
    }

    private void lockAll(ArmorStand as) {
        as.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.ADDING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.ADDING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.ADDING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.ADDING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.ADDING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.ADDING_OR_CHANGING);
    }

    private void removeBalloonInternal(UUID u) {
        ArmorStand as = stands.remove(u);
        if (as != null && !as.isDead()) as.remove();
        Parrot pa = parrots.remove(u);
        if (pa != null && !pa.isDead()) pa.remove();
        active.remove(u);
    }

    public void shutdown() {
        for (UUID u : new ArrayList<>(parrots.keySet())) {
            removeBalloonInternal(u);
        }
    }

    /**
     * Called by listener on teleport to safely re-summon in the new location.
     */
    public void handleTeleport(Player player) {
        UUID u = player.getUniqueId();
        BalloonDefinition def = active.get(u);
        ArmorStand as = stands.get(u);
        ItemStack helmet = as != null ? as.getEquipment().getHelmet() : null;

        removeBalloon(player);

        if (helmet != null) {
            // delay to allow world/chunk to load
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> {
                        summon(player, helmet);
                        if (def != null) active.put(u, def);
                    },
                    BukkitTime.ticks(10)
            );
        } else if (def != null) {
            // Helmet missing? Recreate from definition.
            ItemStack recreated = def.asHelmetItem();
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> {
                        summon(player, recreated);
                        active.put(u, def);
                    },
                    BukkitTime.ticks(10)
            );
        }
    }
}
