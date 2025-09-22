package nl.hauntedmc.serverfeatures.features.glow.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.gui.GuiMenu;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItem;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItems;
import nl.hauntedmc.serverfeatures.common.gui.menu.SimpleMenu;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Builds and opens the Glow selection menu.
 * Layout rules:
 * - Inventory size is 54 (6 rows).
 * - First row is intentionally empty for visual spacing.
 * - First and last column are always empty to create margins.
 * - Color options render in rows 2..5 (index 1..4), columns 2..8 (index 1..7), i.e., a 4 x 7 grid = 28 slots.
 * - Last row (row 6, index 5) contains:
 *     - Slot (5,1) = Remove Glow button
 *     - Slot (5,4) = Current Glow status (center)
 *     - Slot (5,7) = Close button
 */
public final class GlowMenu {

    private GlowMenu() {}

    // Fixed order for readability
    private static final List<NamedTextColor> ORDERED_COLORS = List.of(
            NamedTextColor.BLACK,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED,
            NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD,
            NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.RED,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.YELLOW,
            NamedTextColor.WHITE
    );

    public static void open(Glow feature, Player player) {
        var gui = feature.getLifecycleManager().getGuiManager();

        // Menu skeleton
        SimpleMenu.Builder builder = SimpleMenu.builder(gui)
                .title(feature.getLocalizationHandler().getMessage("glow.menu.title").build())
                .size(54)
                .filler(GuiItems.filler())
                .backButton(false); // Root menu

        // Compute slot indices for a 4x7 grid (rows 1..4, cols 1..7)
        List<Integer> colorSlots = computeColorSlots();
        int i = 0;
        for (NamedTextColor color : ORDERED_COLORS) {
            if (i >= colorSlots.size()) break; // Safety
            int slot = colorSlots.get(i++);
            builder.item(slot, colorItem(feature, color));
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

    private static List<Integer> computeColorSlots() {
        List<Integer> result = new ArrayList<>(28);
        // rows 1 to 4 (inclusive), cols 1 to 7 (inclusive)
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                result.add(slotIndex(row, col));
            }
        }
        return result;
    }

    private static int slotIndex(int row, int col) {
        // row and col are 0-based; inventory is 9-wide
        return row * 9 + col;
    }

    private static GuiItem removeItem(Glow feature) {
        return GuiItem.builder()
                .factory(p -> GuiItems.button(
                        Material.MILK_BUCKET,
                        feature.getLocalizationHandler().getMessage("glow.menu.remove.name").build(),
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
        // Snapshot render: the item itself is static once built. If dynamic updates are needed,
        // one could re-open the same menu after actions (using the manager's reopen helper).
        Component title;
        var active = feature.getGlowHandler().getActiveGlow(viewer);
        if (active.isPresent()) {
            title = feature.getLocalizationHandler()
                    .getMessage("glow.menu.status.active")
                    .withPlaceholders(Map.of("color", pretty(active.get())))
                    .build();
        } else {
            title = feature.getLocalizationHandler()
                    .getMessage("glow.menu.status.inactive")
                    .build();
        }
        Component lore = feature.getLocalizationHandler()
                .getMessage("glow.menu.status.lore").build();

        return GuiItem.builder()
                .factory(p -> GuiItems.info(title, lore))
                .build();
    }

    private static GuiItem closeItem(Glow feature) {
        return GuiItem.builder()
                .factory(p -> GuiItems.button(
                        Material.BARRIER,
                        feature.getLocalizationHandler().getMessage("glow.menu.close.name").build(),
                        feature.getLocalizationHandler().getMessage("glow.menu.close.lore").build()
                ))
                .onClick(ctx -> ctx.player().closeInventory())
                .closeMenuOnClick(true)
                .cooldownMillis(150)
                .build();
    }

    private static GuiItem colorItem(Glow feature, NamedTextColor color) {
        String perm = "serverfeatures.feature.glow.color." + color.toString().toLowerCase();

        return GuiItem.builder()
                // Requires both general use permission and this color permission for visibility
                .visibleWhen(p -> p.hasPermission("serverfeatures.feature.glow.use"))
                .permission(perm)
                .factory(p -> {
                    Component name = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.name")
                            .withPlaceholders(Map.of("color", pretty(color)))
                            .build();
                    Component lore = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.lore.allowed")
                            .build();
                    return GuiItems.button(materialFor(color), name, lore);
                })
                .replacementIfNoPerm(p -> {
                    Component name = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.name")
                            .withPlaceholders(Map.of("color", pretty(color)))
                            .build();
                    Component lore = feature.getLocalizationHandler()
                            .getMessage("glow.menu.color.lore.locked")
                            .build();
                    return GuiItems.button(Material.BARRIER, name, lore);
                })
                .onClick(ctx -> {
                    boolean ok = feature.getGlowHandler().setGlow(ctx.player(), color);
                    if (ok) {
                        ctx.player().sendMessage(
                                feature.getLocalizationHandler()
                                        .getMessage("glow.glow_set")
                                        .withPlaceholders(Map.of("color", color.toString()))
                                        .forAudience(ctx.player())
                                        .build()
                        );
                        // Optionally refresh status by re-opening same menu:
                        // ctx.reopenSame();
                    }
                })
                .cooldownMillis(120)
                .closeMenuOnClick(true)
                .build();
    }

    private static String pretty(NamedTextColor c) {
        String raw = c.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static final Map<NamedTextColor, Material> COLOR_MATERIALS = buildMaterialMap();

    private static Map<NamedTextColor, Material> buildMaterialMap() {
        Map<NamedTextColor, Material> m = new HashMap<>();
        m.put(NamedTextColor.BLACK, Material.BLACK_CONCRETE);
        m.put(NamedTextColor.DARK_BLUE, Material.BLUE_CONCRETE);
        m.put(NamedTextColor.DARK_GREEN, Material.GREEN_CONCRETE);
        m.put(NamedTextColor.DARK_AQUA, Material.CYAN_CONCRETE);
        m.put(NamedTextColor.DARK_RED, Material.RED_CONCRETE);
        m.put(NamedTextColor.DARK_PURPLE, Material.PURPLE_CONCRETE);
        m.put(NamedTextColor.GOLD, Material.ORANGE_CONCRETE);
        m.put(NamedTextColor.GRAY, Material.LIGHT_GRAY_CONCRETE);
        m.put(NamedTextColor.DARK_GRAY, Material.GRAY_CONCRETE);
        m.put(NamedTextColor.BLUE, Material.LIGHT_BLUE_CONCRETE);
        m.put(NamedTextColor.GREEN, Material.LIME_CONCRETE);
        m.put(NamedTextColor.AQUA, Material.LIGHT_BLUE_CONCRETE);
        m.put(NamedTextColor.RED, Material.RED_CONCRETE);
        m.put(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_CONCRETE);
        m.put(NamedTextColor.YELLOW, Material.YELLOW_CONCRETE);
        m.put(NamedTextColor.WHITE, Material.WHITE_CONCRETE);
        return m;
    }

    private static Material materialFor(NamedTextColor color) {
        return COLOR_MATERIALS.getOrDefault(color, Material.WHITE_CONCRETE);
    }
}
