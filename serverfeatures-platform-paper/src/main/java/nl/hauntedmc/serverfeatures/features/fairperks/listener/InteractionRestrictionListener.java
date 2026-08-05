package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class InteractionRestrictionListener implements Listener {

    private final FairPerks feature;

    public InteractionRestrictionListener(FairPerks feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldRestrict(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (feature.settings().restrictions().explodingBeds()
                && block.getBlockData() instanceof Bed
                && block.getWorld().getEnvironment() != World.Environment.NORMAL) {
            deny(player, event);
            return;
        }

        if (feature.settings().restrictions().explodingAnchors()
                && block.getType() == Material.RESPAWN_ANCHOR
                && block.getWorld().getEnvironment() != World.Environment.NETHER
                && block.getBlockData() instanceof RespawnAnchor anchor
                && anchor.getCharges() > 0
                && (event.getItem() == null || event.getItem().getType() != Material.GLOWSTONE)) {
            deny(player, event);
            return;
        }

        if (feature.settings().restrictions().tntIgnite()
                && block.getType() == Material.TNT
                && isIgniter(event.getItem())
                && feature.hostileClassifier().hasNearbyHostile(
                        player,
                        feature.settings().restrictions().tntRadius(),
                        feature.settings().restrictions().tntRadius()
                )) {
            deny(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreeperIgnite(PlayerInteractEntityEvent event) {
        if (!feature.settings().restrictions().creeperIgnite()
                || !(event.getRightClicked() instanceof Creeper)
                || !isIgniter(event.getPlayer().getInventory().getItem(event.getHand()))
                || !shouldRestrict(event.getPlayer())) {
            return;
        }
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndCrystalDamage(EntityDamageByEntityEvent event) {
        if (!feature.settings().restrictions().endCrystals()
                || event.getEntityType() != EntityType.END_CRYSTAL) {
            return;
        }
        Player player = resolvePlayer(event.getDamager());
        if (player != null && shouldRestrict(player)) {
            deny(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (!feature.settings().restrictions().tntPrime()) {
            return;
        }
        Player player = resolvePlayer(event.getPrimingEntity());
        if (player != null && shouldRestrict(player)) {
            deny(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLavaPlacement(PlayerBucketEmptyEvent event) {
        if (!feature.settings().restrictions().lavaNearHostiles()
                || event.getBucket() != Material.LAVA_BUCKET
                || !shouldRestrict(event.getPlayer())) {
            return;
        }
        int radius = feature.settings().restrictions().lavaRadius();
        if (feature.hostileClassifier().hasNearbyHostile(event.getPlayer(), radius, radius)) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!feature.settings().restrictions().blockIgniteNearHostiles()
                || !feature.settings().restrictions().blockIgniteCauses().contains(event.getCause())) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !shouldRestrict(player)) {
            return;
        }
        int radius = feature.settings().restrictions().igniteRadius();
        if (feature.hostileClassifier().hasNearbyHostile(player, radius, radius)) {
            deny(player, event);
        }
    }

    private boolean shouldRestrict(Player player) {
        return !player.hasPermission(FairPerks.RESTRICTION_BYPASS_PERMISSION)
                && feature.stateService().isRestricted(player);
    }

    private void deny(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        feature.sendActionBar(
                player,
                "fairperks.restriction.interaction."
                        + feature.stateService().activeRestrictionMessageSuffix(player)
        );
    }

    private static boolean isIgniter(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE;
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
