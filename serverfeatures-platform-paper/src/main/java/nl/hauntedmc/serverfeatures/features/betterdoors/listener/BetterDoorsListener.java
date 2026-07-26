package nl.hauntedmc.serverfeatures.features.betterdoors.listener;

import nl.hauntedmc.serverfeatures.features.betterdoors.internal.BetterDoorsHandler;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BetterDoorsListener implements Listener {

    private final BetterDoorsHandler handler;

    public BetterDoorsListener(BetterDoorsHandler handler) {
        this.handler = handler;
    }

    /**
     * Double doors (player interaction): mirror neighbor **next tick**
     * after vanilla has toggled the primary door.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDoorUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!handler.isDoor(clicked)) return;

        handler.mirrorNextTick(clicked);
    }

    /**
     * Knock (player): left-click any door with main hand → play knock sound.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDoorKnock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!handler.isDoor(clicked)) return;

        handler.playKnock(clicked);
    }

    /**
     * Double doors (redstone): synchronize neighbor **immediately** to the open/close state
     * implied by power level.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        Block b = event.getBlock();
        if (!handler.isDoor(b)) return;

        boolean wasPowered = event.getOldCurrent() > 0;
        boolean nowPowered = event.getNewCurrent() > 0;
        if (wasPowered == nowPowered) return;

        // doors open when powered
        handler.mirrorImmediate(b, nowPowered);
    }
}
