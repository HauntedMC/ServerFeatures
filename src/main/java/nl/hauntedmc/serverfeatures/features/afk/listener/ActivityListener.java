package nl.hauntedmc.serverfeatures.features.afk.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.util.Movement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

public class ActivityListener implements Listener {

    private final AFK feature;

    public ActivityListener(AFK feature) { this.feature = feature; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        feature.getService().handleJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        feature.getService().handleLeave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() ->
                feature.getService().fire(AfkEvent.simple(p, AfkEventType.CHAT)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        feature.getService().fire(AfkEvent.command(e.getPlayer(), e.getMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        Movement mv = new Movement(
                from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(),
                to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch()
        );
        feature.getService().fire(AfkEvent.move(e.getPlayer(), mv));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.JUMP));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p)
            feature.getService().fire(AfkEvent.simple(p, AfkEventType.INVENTORY_STRONG));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p)
            feature.getService().fire(AfkEvent.simple(p, AfkEventType.INVENTORY_STRONG));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p)
            feature.getService().fire(AfkEvent.simple(p, AfkEventType.INVENTORY_STRONG));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p)
            feature.getService().fire(AfkEvent.simple(p, AfkEventType.INVENTORY_STRONG));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.STRONG_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.STRONG_ACTION));
    }

    // Weak/non-strong player actions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.WEAK_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.STRONG_ACTION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        var from = e.getFrom(); var to = e.getTo();
        Movement mv = new Movement(
                from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(),
                to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch()
        );
        feature.getService().fire(AfkEvent.teleport(e.getPlayer(), mv));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            feature.getService().fire(AfkEvent.simple(p, AfkEventType.STRONG_ACTION));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent e) {
        feature.getService().fire(AfkEvent.simple(e.getPlayer(), AfkEventType.STRONG_ACTION));
    }
}
