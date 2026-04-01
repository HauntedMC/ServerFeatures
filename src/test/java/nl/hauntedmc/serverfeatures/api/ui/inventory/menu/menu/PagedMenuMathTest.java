package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagedMenuMathTest {

    @Test
    void totalPagesNeverDropsBelowOne() {
        assertEquals(1, PagedMenuMath.totalPages(0, 10));
        assertEquals(1, PagedMenuMath.totalPages(1, 10));
        assertEquals(1, PagedMenuMath.totalPages(10, 10));
    }

    @Test
    void totalPagesRoundsUpAcrossPartialPage() {
        assertEquals(2, PagedMenuMath.totalPages(11, 10));
        assertEquals(3, PagedMenuMath.totalPages(25, 10));
    }

    @Test
    void clampPageIndexRespectsLowerAndUpperBounds() {
        assertEquals(0, PagedMenuMath.clampPageIndex(-5, 25, 10));
        assertEquals(2, PagedMenuMath.clampPageIndex(99, 25, 10));
        assertEquals(1, PagedMenuMath.clampPageIndex(1, 25, 10));
    }
}
