package nl.hauntedmc.serverfeatures.api.ui.world.display.handle;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Display;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayVisualHandleTest {

    @Test
    void clearRemovesOnlyLiveDisplaysAndIsIdempotent() {
        AtomicInteger removes = new AtomicInteger();
        Display live = InterfaceProxy.of(Display.class, Map.of(
                "isDead", args -> false,
                "remove", args -> {
                    removes.incrementAndGet();
                    return null;
                }
        ));
        Display dead = InterfaceProxy.of(Display.class, Map.of(
                "isDead", args -> true,
                "remove", args -> {
                    removes.incrementAndGet();
                    return null;
                }
        ));

        List<Display> displays = new ArrayList<>(List.of(live, dead));
        DisplayVisualHandle handle = new DisplayVisualHandle(displays);

        handle.clear();
        handle.clear();

        assertTrue(handle.isCleared());
        assertTrue(displays.isEmpty());
        assertEquals(1, removes.get());
    }
}
