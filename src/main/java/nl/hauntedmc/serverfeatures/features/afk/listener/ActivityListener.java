package nl.hauntedmc.serverfeatures.features.afk.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;

public class ActivityListener implements Listener {

    private final AFK feature;

    public ActivityListener(AFK feature) { this.feature = feature; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { feature.getService().handleJoin(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { feature.getService().handleLeave(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(feature.getPlugin(), () -> feature.getService().onChat(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        feature.getService().onCommand(e.getPlayer(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (!from.getWorld().equals(to.getWorld())) {
            feature.getService().onWeakAction(e.getPlayer());
            return;
        }
        feature.getService().onMove(
                e.getPlayer(),
                from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(),
                to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    // Strong: instantly clears AFK and refreshes activity
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) feature.getService().onInventoryClick(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) feature.getService().onInventoryClick(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) feature.getService().onInventoryClick(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) feature.getService().onInventoryClick(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { feature.getService().onStrongAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { feature.getService().onStrongAction(e.getPlayer()); }

    // Weak: does not refresh activity unless combined with movement (when AFK)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) { feature.getService().onWeakAction(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) { feature.getService().onWeakAction(e.getPlayer()); }
}
