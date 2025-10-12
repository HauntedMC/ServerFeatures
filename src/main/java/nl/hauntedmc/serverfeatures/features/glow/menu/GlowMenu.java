package nl.hauntedmc.serverfeatures.features.glow.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.GuiMenu;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.item.GuiItem;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.item.GuiItemHelper;
import nl.hauntedmc.serverfeatures.api.gui.invmenu.menu.SimpleMenu;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Builds and opens the Glow selection menu.
 * Layout rules:
 * - Inventory size is 54 (6 rows).
 * - First row is intentionally empty for visual spacing.
 * - First and last column are always empty to create margins.
 * - Options render in rows 2..5 (index 1..4), columns 2..8 (index 1..7), i.e., a 4 x 7 grid = 28 slots.
 * - Last row (row 6, index 5) contains:
 *     - Slot (5,1) = Remove Glow button
 *     - Slot (5,4) = Current Glow status (center)
 *     - Slot (5,7) = Close button
 * All lore lines are preceded by an empty line for nicer spacing.
 */
public final class GlowMenu {

    private GlowMenu() {}

    // Empty line component for lore spacing
    private static final Component EMPTY_LORE = Component.text(" ");
    private static final int LORE_MAX_WIDTH = 30;

    public static void open(Glow feature, Player player) {
        var gui = feature.getLifecycleManager().getGuiManager();

        // Menu skeleton
        SimpleMenu.Builder builder = SimpleMenu.builder(gui)
                .title(feature.getLocalizationHandler().getMessage("glow.menu.title").build())
                .size(54)
                .filler(GuiItemHelper.filler())
                .backButton(false); // Root menu

        // Compute slot indices for a 4x7 grid (rows 1..4, cols 1..7)
        List<Integer> optionSlots = computeGridSlots(4, 7);
        List<GlowEffect> effects = feature.getGlowRegistry().all();

        int i = 0;
        for (GlowEffect effect : effects) {
            if (i >= optionSlots.size()) break; // Safety
            int slot = optionSlots.get(i++);
            builder.item(slot, effectItem(feature, effect));
        }

        // Last row controls
        int removeSlot = slotIndex(5, 1); // row 5, col 1 (0-indexed rows/cols)
        int statusSlot = slotIndex(5, 4); // center
        int closeSlot  = slotIndex(5, 7);

        builder.item(removeSlot, removeItem(feature));
        builder.item(statusSlot, statusItem(feature, player));
        builder.item(closeSlot, closeItem(feature));

        GuiMenu menu = builder.build();
        gui.openRoot(player, menu);
    }

    private static List<Integer> computeGridSlots(int rows, int cols) {
        // rows 1..rows, cols 1..cols within a 9-wide inventory
        List<Integer> result = new ArrayList<>(rows * cols);
        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= cols; col++) {
                result.add(slotIndex(row, col));
            }
        }
        return result;
    }

    private static int slotIndex(int row, int col) {
        return row * 9 + col;
    }

    private static GuiItem removeItem(Glow feature) {
        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(
                        Material.MILK_BUCKET,
                        feature.getLocalizationHandler().getMessage("glow.menu.remove.name").build(),
                        LORE_MAX_WIDTH,
                        EMPTY_LORE,
                        feature.getLocalizationHandler().getMessage("glow.menu.remove.lore").build()
                ))
                .onClick(ctx -> {
                    boolean had = feature.getGlowHandler().hasActiveGlow(ctx.player());
                    boolean removed = feature.getGlowHandler().removeGlow(ctx.player());
                    if (removed) {
                        if (had) {
                            ctx.player().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("glow.glow_removed").forAudience(ctx.player()).build());
                        } else {
                            ctx.player().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("glow.no_active_glow").forAudience(ctx.player()).build());
                        }
                    }
                })
                .closeMenuOnClick(true)
                .cooldownMillis(150)
                .build();
    }

    private static GuiItem statusItem(Glow feature, Player viewer) {
        // Snapshot render of status
        Component title;
        var active = feature.getGlowHandler().getActiveGlow(viewer);
        if (active.isPresent()) {
            title = feature.getLocalizationHandler()
                    .getMessage("glow.menu.status.active")
                    .with("color", active.get().displayName(viewer))
                    .build();
        } else {
            title = feature.getLocalizationHandler()
                    .getMessage("glow.menu.status.inactive")
                    .build();
        }
        Component lore = feature.getLocalizationHandler()
                .getMessage("glow.menu.status.lore").build();

        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(Material.CLOCK, title, LORE_MAX_WIDTH, EMPTY_LORE, lore))
                .build();
    }

    private static GuiItem closeItem(Glow feature) {
        return GuiItem.builder()
                .factory(p -> GuiItemHelper.menuItemWrapped(
                        Material.BARRIER,
                        feature.getLocalizationHandler().getMessage("glow.menu.close.name").build(),
                        LORE_MAX_WIDTH,
                        EMPTY_LORE,
                        feature.getLocalizationHandler().getMessage("glow.menu.close.lore").build()
                ))
                .onClick(ctx -> ctx.player().closeInventory())
                .closeMenuOnClick(true)
                .cooldownMillis(150)
                .build();
    }

    private static GuiItem effectItem(Glow feature, GlowEffect effect) {
        String perm = effect.permission();

        return GuiItem.builder()
                // Requires general use permission; specific permission is declared with .permission()
                .visibleWhen(p -> p.hasPermission("serverfeatures.feature.glow.use"))
                .permission(perm)
                .factory(p -> {
                    Component name = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.name")
                            .with("color", effect.displayName(p))
                            .build();
                    Component lore = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.lore.allowed")
                            .build();
                    Material mat = effect.menuMaterial();
                    return GuiItemHelper.menuItemWrapped(mat, name, LORE_MAX_WIDTH, EMPTY_LORE, lore);
                })
                .replacementIfNoPerm(p -> {
                    Component name = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.name")
                            .with("color", effect.displayName(p))
                            .build();
                    Component lore = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.lore.locked")
                            .build();
                    return GuiItemHelper.menuItemWrapped(Material.BARRIER, name, LORE_MAX_WIDTH, EMPTY_LORE, lore);
                })
                .onClick(ctx -> {
                    boolean ok = feature.getGlowHandler().setGlow(ctx.player(), effect);
                    if (ok) {
                        ctx.player().sendMessage(
                                feature.getLocalizationHandler()
                                        .getMessage("glow.glow_set")
                                        .with("color", effect.displayName(ctx.player()))
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
