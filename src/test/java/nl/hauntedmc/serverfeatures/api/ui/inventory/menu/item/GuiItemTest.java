package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiItemTest {

    @Test
    void builderRequiresFactory() {
        assertThrows(IllegalArgumentException.class, () -> GuiItem.builder()
                .factory(null)
                .build());
    }

    @Test
    void visibilityCombinesPredicateAndPermission() {
        Player noPerm = player(false, UUID.randomUUID(), new AtomicInteger());
        Player withPerm = player(true, UUID.randomUUID(), new AtomicInteger());

        GuiItem item = GuiItem.builder()
                .factory(p -> null)
                .visibleWhen(p -> true)
                .permission("perm.test")
                .build();

        assertTrue(item.visibleTo(withPerm));
        assertEquals(false, item.visibleTo(noPerm));
    }

    @Test
    void replacementIsReturnedOnlyWhenItemIsNotVisible() {
        ItemStack replacement = null;
        Player noPerm = player(false, UUID.randomUUID(), new AtomicInteger());
        Player withPerm = player(true, UUID.randomUUID(), new AtomicInteger());

        GuiItem item = GuiItem.builder()
                .factory(p -> null)
                .permission("perm.test")
                .replacementIfNoPerm(p -> replacement)
                .build();

        assertNull(item.replacementOrNull(withPerm));
        assertEquals(replacement, item.replacementOrNull(noPerm));
    }

    @Test
    void cooldownDebouncesRapidRepeatedClicks() {
        AtomicInteger clicks = new AtomicInteger();
        GuiItem item = GuiItem.builder()
                .factory(p -> null)
                .onClick(ctx -> clicks.incrementAndGet())
                .cooldownMillis(1_000)
                .build();
        Player player = player(true, UUID.randomUUID(), new AtomicInteger());

        item.click(player, null);
        item.click(player, null);

        assertEquals(1, clicks.get());
    }

    @Test
    void closeOnClickClosesInventoryAfterAction() {
        AtomicInteger closeCalls = new AtomicInteger();
        GuiItem item = GuiItem.builder()
                .factory(p -> null)
                .closeMenuOnClick(true)
                .build();
        Player player = player(true, UUID.randomUUID(), closeCalls);

        item.click(player, null);

        assertEquals(1, closeCalls.get());
    }

    @Test
    void onClickExceptionsAreSwallowed() {
        GuiItem item = GuiItem.builder()
                .factory(p -> null)
                .onClick(ctx -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        Player player = player(true, UUID.randomUUID(), new AtomicInteger());
        item.click(player, null);
        assertTrue(item.visibleTo(player));
    }

    private static Player player(boolean hasPermission, UUID id, AtomicInteger closeCalls) {
        return InterfaceProxy.of(Player.class, Map.of(
                "hasPermission", args -> hasPermission,
                "getUniqueId", args -> id,
                "closeInventory", args -> {
                    closeCalls.incrementAndGet();
                    return null;
                }
        ));
    }
}
