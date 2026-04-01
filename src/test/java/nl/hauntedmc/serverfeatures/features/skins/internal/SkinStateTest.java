package nl.hauntedmc.serverfeatures.features.skins.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinStateTest {

    @Test
    void usesConfiguredCooldownSeconds() {
        SkinState state = new SkinState(path -> 45);

        assertEquals(45, state.getCooldownSeconds());
    }

    @Test
    void tracksLastUsePerPlayer() {
        SkinState state = new SkinState(path -> 1);
        UUID uuid = UUID.randomUUID();

        state.setLastUse(uuid, 3_000L);
        assertEquals(3_000L, state.getLastUse(uuid));

        state.clearLastUse(uuid);
        assertEquals(0L, state.getLastUse(uuid));
    }

    @Test
    void tracksWhetherCustomSkinIsApplied() {
        SkinState state = new SkinState(path -> 1);
        UUID uuid = UUID.randomUUID();

        assertFalse(state.hasCustomSkin(uuid));

        state.markCustomSkin(uuid, true);
        assertTrue(state.hasCustomSkin(uuid));

        state.markCustomSkin(uuid, false);
        assertFalse(state.hasCustomSkin(uuid));
    }

    @Test
    void clearAllResetsEntireState() {
        SkinState state = new SkinState(path -> 1);
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();

        state.setLastUse(one, 1_000L);
        state.markCustomSkin(two, true);
        state.clearAll();

        assertEquals(0L, state.getLastUse(one));
        assertFalse(state.hasCustomSkin(two));
    }
}

