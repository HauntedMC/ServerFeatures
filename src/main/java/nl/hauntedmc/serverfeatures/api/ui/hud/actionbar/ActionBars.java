package nl.hauntedmc.serverfeatures.api.ui.hud.actionbar;

import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl.NoopActionBarAPI;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global access to the ActionBarAPI.
 * Platform code must call bootstrap(...) during startup and shutdown() during disable.
 * If not bootstrapped, a strict no-op service is returned (safe to call, zero work).
 */
public final class ActionBars {
    private static final AtomicReference<ActionBarAPI> REF = new AtomicReference<>();

    private ActionBars() {}

    public static void bootstrap(@NotNull ActionBarAPI service) {
        REF.set(service);
    }

    public static void shutdown() {
        REF.set(null);
    }

    /** Never null. Returns a no-op implementation if not bootstrapped. */
    public static @NotNull ActionBarAPI service() {
        ActionBarAPI s = REF.get();
        return (s != null) ? s : NoopActionBarAPI.INSTANCE;
    }
}
