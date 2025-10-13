package nl.hauntedmc.serverfeatures.features.balloons.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.item.GuiItem;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.item.GuiItemHelper;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.menu.PagedMenu;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.model.BalloonDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Paged Balloons selection menu with the same visual style as Glow.
 * Layout (6 rows total):
 *   - Row 0: filler (glass)
 *   - Rows 1..3: content grid (3 x 7, inner columns 1..7)
 *   - Row 4: filler (glass)  <-- kept empty as requested
 *   - Row 5: controls (prev @45, remove @46, status @49, close @52, next @53)
 * If the player lacks permission for a balloon, it renders as BARRIER with a "locked" lore.
 */
public final class BalloonsMenu {

    private BalloonsMenu() {}

    private static final Component EMPTY_LORE = Component.text(" ");
    private static final int LORE_MAX_WIDTH = 30;

    public static void open(Balloons feature, Player player) {
        var gui = feature.getLifecycleManager().getGuiManager();

        List<BalloonDefinition> defs = feature.getRegistry().all();

        // Content grid: 3 rows x 7 columns (rows 1..3, cols 1..7) to keep margins and an extra glass row above controls.
        List<Integer> contentSlots = computeGridSlots(3, 7);

        int prevSlot = 45; // bottom-left arrow
        int nextSlot = 53; // bottom-right arrow

        int removeSlot = 46; // last row
        int statusSlot = 49; // last row, middle
        int closeSlot  = 52; // last row, near next

        PagedMenu<BalloonDefinition> menu = PagedMenu.<BalloonDefinition>builder(gui)
                .title(feature.getLocalizationHandler().getMessage("balloons.menu.title").build())
                .size(54)
                .showPageInTitle(true)
                .filler(GuiItemHelper.filler())
                .backButton(false)
                .item(removeSlot, removeItem(feature))
                .item(statusSlot, statusItem(feature, player))
                .item(closeSlot, closeItem(feature))
                .entries(defs)
                .renderer(def -> balloonItem(feature, def))
                .contentSlots(contentSlots)
                .prevSlot(prevSlot)
                .nextSlot(nextSlot)
                .prevLabel(feature.getLocalizationHandler().getMessage("balloons.menu.prev").build())
                .nextLabel(feature.getLocalizationHandler().getMessage("balloons.menu.next").build())
                .pageInfoSlot(Optional.empty()) // use title for page info
                .build();

        gui.openRoot(player, menu);
    }

    /** rows = number of rows (starting at 1), cols = number of columns (starting at 1). */
    private static List<Integer> computeGridSlots(int rows, int cols) {
        // rows 1..rows, cols 1..cols within a 9-wide inventory, leaving outer margins (col 0 and 8 empty)
        List<Integer> result = new ArrayList<>(rows * cols);
        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= cols; col++) {
                result.add(slotIndex(row, col));
            }
        }
        return result;
    }

    /** Maps our (row, col) with margins to inventory slot index. */
    private static int slotIndex(int row, int col) {
        // Inventory is 9-wide, 0-indexed rows in Bukkit. Here we treat 'row' and 'col' as the inner grid coords.
        return row * 9 + col;
    }

    private static GuiItem removeItem(Balloons feature) {
        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(
                        Material.MILK_BUCKET,
                        feature.getLocalizationHandler().getMessage("balloons.menu.remove.name").build(),
                        LORE_MAX_WIDTH,
                        EMPTY_LORE,
                        feature.getLocalizationHandler().getMessage("balloons.menu.remove.lore").build()
                ))
                .onClick(ctx -> {
                    boolean removed = feature.getHandler().removeBalloon(ctx.player());
                    if (removed) {
                        ctx.player().sendMessage(
                                feature.getLocalizationHandler()
                                        .getMessage("balloons.removed")
                                        .forAudience(ctx.player()).build()
                        );
                    } else {
                        ctx.player().sendMessage(
                                feature.getLocalizationHandler()
                                        .getMessage("balloons.no_active")
                                        .forAudience(ctx.player()).build()
                        );
                    }
                })
                .closeMenuOnClick(true)
                .cooldownMillis(150)
                .build();
    }

    private static GuiItem statusItem(Balloons feature, Player viewer) {
        Component title;
        var active = feature.getHandler().getActiveBalloon(viewer);
        if (active.isPresent()) {
            title = feature.getLocalizationHandler()
                    .getMessage("balloons.menu.status.active")
                    .with("name", active.get().displayName())
                    .build();
        } else {
            title = feature.getLocalizationHandler()
                    .getMessage("balloons.menu.status.inactive").build();
        }
        Component lore = feature.getLocalizationHandler()
                .getMessage("balloons.menu.status.lore").build();

        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(Material.CLOCK, title, LORE_MAX_WIDTH, EMPTY_LORE, lore))
                .build();
    }

    private static GuiItem closeItem(Balloons feature) {
        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(
                        Material.BARRIER,
                        feature.getLocalizationHandler().getMessage("balloons.menu.close.name").build(),
                        LORE_MAX_WIDTH,
                        EMPTY_LORE,
                        feature.getLocalizationHandler().getMessage("balloons.menu.close.lore").build()
                ))
                .onClick(ctx -> ctx.player().closeInventory())
                .closeMenuOnClick(true)
                .cooldownMillis(150)
                .build();
    }

    private static GuiItem balloonItem(Balloons feature, BalloonDefinition def) {
        String perm = def.permission();
        return GuiItem.builder()
                .visibleWhen(p -> p.hasPermission("serverfeatures.feature.balloons.use"))
                .permission(perm)
                .factory(p -> {
                    var name = feature.getLocalizationHandler()
                            .getMessage("balloons.menu.balloon.name")
                            .with("name", def.displayName())
                            .build();

                    var lore = feature.getLocalizationHandler()
                            .getMessage("balloons.menu.balloon.lore.allowed")
                            .build();

                    var icon = def.asMenuIcon();
                    var meta = icon.getItemMeta();
                    meta.displayName(name);
                    meta.lore(List.of(EMPTY_LORE, lore.decoration(TextDecoration.ITALIC, false)));
                    icon.setItemMeta(meta);
                    return icon;
                })
                .replacementIfNoPerm(p -> {
                    var name = feature.getLocalizationHandler()
                            .getMessage("balloons.menu.balloon.name")
                            .with("name", def.displayName())
                            .build();

                    var lore = feature.getLocalizationHandler()
                            .getMessage("balloons.menu.balloon.lore.locked")
                            .build();

                    var icon = new org.bukkit.inventory.ItemStack(Material.BARRIER);
                    var meta = icon.getItemMeta();
                    meta.displayName(name);
                    meta.lore(List.of(EMPTY_LORE, lore));
                    icon.setItemMeta(meta);
                    return icon;
                })
                .onClick(ctx -> {
                    boolean ok = feature.getHandler().setBalloon(ctx.player(), def);
                    if (ok) {
                        ctx.player().sendMessage(
                                feature.getLocalizationHandler()
                                        .getMessage("balloons.set")
                                        .with("name", def.displayName())
                                        .forAudience(ctx.player())
                                        .build()
                        );
                    }
                })
                .cooldownMillis(120)
                .closeMenuOnClick(true)
                .build();
    }

}
