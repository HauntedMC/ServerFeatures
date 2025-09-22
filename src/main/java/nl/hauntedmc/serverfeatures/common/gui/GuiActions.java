// NEW FILE: nl/hauntedmc/serverfeatures/common/gui/GuiActions.java
package nl.hauntedmc.serverfeatures.common.gui;

import nl.hauntedmc.serverfeatures.common.gui.item.GuiClickContext;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Convenience action builders for GuiItem.onClick(...):
 * - Run player/console commands (supports %player% placeholder)
 * - Send chat messages
 * - Give items
 * - Open child/root menus via Supplier
 * - Play sounds
 */
public final class GuiActions {
    private GuiActions() {}

    public static Consumer<GuiClickContext> runPlayerCommand(String commandTemplate) {
        return ctx -> {
            String cmd = commandTemplate.replace("%player%", ctx.player().getName());
            ctx.player().performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        };
    }

    public static Consumer<GuiClickContext> runConsoleCommand(String commandTemplate) {
        return ctx -> {
            String cmd = commandTemplate.replace("%player%", ctx.player().getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        };
    }

    public static Consumer<GuiClickContext> sendMessage(net.kyori.adventure.text.Component message) {
        return ctx -> ctx.player().sendMessage(message);
    }

    public static Consumer<GuiClickContext> giveItem(Supplier<ItemStack> supplier) {
        return ctx -> {
            ItemStack is = supplier.get();
            if (is == null) return;
            var leftover = ctx.player().getInventory().addItem(is);
            // drop leftover at feet as a fallback (rare, but robust)
            leftover.values().forEach(drop -> ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), drop));
        };
    }

    public static Consumer<GuiClickContext> openChild(Supplier<nl.hauntedmc.serverfeatures.common.gui.GuiMenu> supplier) {
        return ctx -> ctx.openChild(supplier.get());
    }

    public static Consumer<GuiClickContext> openRoot(Supplier<nl.hauntedmc.serverfeatures.common.gui.GuiMenu> supplier) {
        return ctx -> ctx.openRoot(supplier.get());
    }

    public static Consumer<GuiClickContext> playSound(Sound sound, float volume, float pitch) {
        return ctx -> ctx.player().playSound(ctx.player().getLocation(), sound, volume, pitch);
    }
}
