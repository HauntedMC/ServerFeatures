package nl.hauntedmc.serverfeatures.api.visualisation.display;

import nl.hauntedmc.serverfeatures.api.visualisation.VisualHandle;
import org.bukkit.entity.Display;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DisplayVisualHandle implements VisualHandle {
        private final List<Display> displays;
        private final AtomicBoolean cleared = new AtomicBoolean(false);

        DisplayVisualHandle(List<Display> displays) {
            this.displays = displays;
        }

        @Override
        public void clear() {
            if (cleared.compareAndSet(false, true)) {
                for (Display d : displays) {
                    if (d != null && !d.isDead()) d.remove();
                }
                displays.clear();
            }
        }

        @Override
        public boolean isCleared() {
            return cleared.get();
        }
    }