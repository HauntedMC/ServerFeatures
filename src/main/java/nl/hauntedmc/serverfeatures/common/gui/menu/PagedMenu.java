package nl.hauntedmc.serverfeatures.common.gui.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.gui.GuiMenu;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiClickContext;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItem;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItems;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureGUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

/**
 * Multi-page menu with prev/next navigation and optional page info.
 */
public final class PagedMenu<T> extends GuiMenu {

    private final List<T> entries;
    private final Function<T, GuiItem> renderer;
    private final List<Integer> contentSlots;
    private final int prevSlot;
    private final int nextSlot;
    private final Optional<Integer> pageInfoSlot;

    private int pageIndex = 0;

    private final Map<Integer, GuiItem> dynamicItems = new HashMap<>();

    private PagedMenu(
            FeatureGUIManager gui,
            Component baseTitle,
            int size,
            boolean showPageInTitle,
            ItemStack filler,
            Map<Integer, GuiItem> items,
            boolean addBackButton,
            int backSlot,
            List<T> entries,
            Function<T, GuiItem> renderer,
            List<Integer> contentSlots,
            int prevSlot,
            int nextSlot,
            Optional<Integer> pageInfoSlot
    ) {
        super(gui, baseTitle, size, showPageInTitle, filler, items, addBackButton, backSlot);
        this.entries = new ArrayList<>(entries);
        this.renderer = renderer;
        this.contentSlots = new ArrayList<>(contentSlots);
        this.prevSlot = prevSlot;
        this.nextSlot = nextSlot;
        this.pageInfoSlot = pageInfoSlot;
    }

    public static <T> Builder<T> builder(FeatureGUIManager gui) { return new Builder<>(gui); }

    @Override
    public Component titleFor(Player p) {
        if (!showPageInTitle) return baseTitle;
        int perPage = contentSlots.size();
        int total = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        return baseTitle.append(Component.text(" (" + (pageIndex + 1) + "/" + total + ")"));
    }

    @Override
    protected void afterPopulate(Player p, Inventory inv) {
        renderPage(p, inv);
    }

    private void renderPage(Player p, Inventory inv) {
        for (int s : contentSlots) {
            inv.setItem(s, null);
            dynamicItems.remove(s);
        }

        int perPage = contentSlots.size();
        int start = pageIndex * perPage;

        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= entries.size()) break;
            GuiItem gi = renderer.apply(entries.get(idx));
            int slot = contentSlots.get(i);
            inv.setItem(slot, gi.renderFor(p));
            dynamicItems.put(slot, gi);
        }

        inv.setItem(prevSlot, GuiItems.button(Material.ARROW, Component.text("Previous")));
        inv.setItem(nextSlot, GuiItems.button(Material.ARROW, Component.text("Next")));

        pageInfoSlot.ifPresent(s -> {
            int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
            ItemStack info = GuiItems.info(Component.text("Page " + (pageIndex + 1) + "/" + totalPages));
            inv.setItem(s, info);
        });
    }

    @Override
    public void handleClick(Player p, int slot, InventoryClickEvent e) {
        if (slot == prevSlot) {
            if (pageIndex > 0) {
                pageIndex--;
                gui.reopenSame(p, this);
            }
            return;
        }
        if (slot == nextSlot) {
            int perPage = contentSlots.size();
            int maxPage = Math.max(0, (int) Math.ceil(entries.size() / (double) perPage) - 1);
            if (pageIndex < maxPage) {
                pageIndex++;
                gui.reopenSame(p, this);
            }
            return;
        }
        GuiItem gi = dynamicItems.get(slot);
        if (gi != null && gi.visibleTo(p)) {
            gi.click(p, new GuiClickContext(this, slot, e));
            return;
        }
        super.handleClick(p, slot, e);
    }

    public int pageIndex() { return pageIndex; }

    /** Clamp and set the current page index. */
    public void setPageIndex(int page) {
        if (page < 0) page = 0;
        int perPage = contentSlots.size();
        int max = Math.max(0, (int) Math.ceil(entries.size() / (double) perPage) - 1);
        this.pageIndex = Math.min(page, max);
    }

    public static final class Builder<T> {
        private final FeatureGUIManager gui;
        private Component title = Component.text("Menu");
        private int size = 9 * 6;
        private boolean pageInTitle = true;
        private ItemStack filler = null;
        private final Map<Integer, GuiItem> items = new HashMap<>();
        private boolean backButton = true;
        private int backSlot = -1;

        private List<T> entries = List.of();
        private Function<T, GuiItem> renderer = t -> GuiItems.empty();
        private List<Integer> contentSlots = defaultGrid();
        private int prevSlot = 45; // bottom-left
        private int nextSlot = 53; // bottom-right
        private Optional<Integer> pageInfoSlot = Optional.of(49);

        private Builder(FeatureGUIManager gui) {
            this.gui = Objects.requireNonNull(gui, "gui");
        }

        public Builder<T> title(Component t) { this.title = t; return this; }
        public Builder<T> size(int s) { this.size = s; return this; }
        public Builder<T> showPageInTitle(boolean b) { this.pageInTitle = b; return this; }
        public Builder<T> filler(ItemStack i) { this.filler = i; return this; }
        public Builder<T> item(int slot, GuiItem item) { this.items.put(slot, item); return this; }
        public Builder<T> backButton(boolean enabled) { this.backButton = enabled; return this; }
        public Builder<T> backButtonSlot(int slot) { this.backSlot = slot; return this; }

        public Builder<T> entries(List<T> es) { this.entries = es; return this; }
        public Builder<T> renderer(Function<T, GuiItem> r) { this.renderer = r; return this; }
        public Builder<T> contentSlots(List<Integer> s) { this.contentSlots = s; return this; }
        public Builder<T> prevSlot(int s) { this.prevSlot = s; return this; }
        public Builder<T> nextSlot(int s) { this.nextSlot = s; return this; }
        public Builder<T> pageInfoSlot(Optional<Integer> s) { this.pageInfoSlot = s; return this; }

        public PagedMenu<T> build() {
            if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid size");
            if (contentSlots == null || contentSlots.isEmpty()) throw new IllegalArgumentException("contentSlots cannot be empty");
            if (prevSlot < 0 || prevSlot >= size || nextSlot < 0 || nextSlot >= size) {
                throw new IllegalArgumentException("Prev/Next slots must be within inventory bounds");
            }
            pageInfoSlot.ifPresent(s -> {
                if (s < 0 || s >= size) throw new IllegalArgumentException("pageInfoSlot out of bounds");
            });
            validateFixedItems(items, size, backButton, backSlot, "PagedMenu");

            Set<Integer> reserved = new HashSet<>(contentSlots);
            ensureSlotsInBounds(contentSlots, size, "contentSlots");
            if (reserved.contains(prevSlot) || reserved.contains(nextSlot) || pageInfoSlot.map(reserved::contains).orElse(false))
                throw new IllegalArgumentException("contentSlots cannot include prev/next/pageInfo slots");
            if (backButton && backSlot >= 0 && (reserved.contains(backSlot)))
                throw new IllegalArgumentException("contentSlots cannot include back slot");

            for (int s : items.keySet()) {
                if (s == prevSlot || s == nextSlot || pageInfoSlot.orElse(-1) == s)
                    throw new IllegalArgumentException("Fixed items cannot override prev/next/pageInfo slots");
                if (backButton && backSlot >= 0 && s == backSlot)
                    throw new IllegalArgumentException("Fixed items cannot override back button slot");
                if (contentSlots.contains(s))
                    throw new IllegalArgumentException("Fixed items cannot be placed in contentSlots");
            }

            return new PagedMenu<>(gui, title, size, pageInTitle, filler, items, backButton, backSlot,
                    entries, renderer, contentSlots, prevSlot, nextSlot, pageInfoSlot);
        }

        private static List<Integer> defaultGrid() {
            // For a 6-row menu, use rows 2..5 (indexes 9..44); last row reserved for nav
            List<Integer> s = new ArrayList<>();
            for (int row = 1; row <= 4; row++) {
                for (int col = 0; col < 9; col++) s.add(row * 9 + col);
            }
            return s;
        }
    }
}
