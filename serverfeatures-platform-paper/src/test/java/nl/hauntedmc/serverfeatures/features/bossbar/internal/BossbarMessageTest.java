package nl.hauntedmc.serverfeatures.features.bossbar.internal;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossbarMessageTest {

    @Test
    void builderUsesDefaultsWhenNotOverridden() {
        BossbarMessage message = new BossbarMessage.Builder()
                .messageKey("welcome")
                .build();

        assertEquals("welcome", message.getMessageKey());
        assertEquals(100L, message.getDurationTicks());
        assertEquals(BarColor.WHITE, message.getColor());
        assertEquals(BarStyle.SOLID, message.getStyle());
        assertEquals(1.0D, message.getInitialProgress());
        assertFalse(message.isAutoFade());
        assertTrue(message.getFlags().isEmpty());
    }

    @Test
    void builderAppliesExplicitValues() {
        Set<BarFlag> flags = Set.of(BarFlag.CREATE_FOG);

        BossbarMessage message = new BossbarMessage.Builder()
                .messageKey("x")
                .durationTicks(40L)
                .color(BarColor.BLUE)
                .style(BarStyle.SEGMENTED_6)
                .initialProgress(0.25D)
                .autoFade(true)
                .flags(flags)
                .build();

        assertEquals("x", message.getMessageKey());
        assertEquals(40L, message.getDurationTicks());
        assertEquals(BarColor.BLUE, message.getColor());
        assertEquals(BarStyle.SEGMENTED_6, message.getStyle());
        assertEquals(0.25D, message.getInitialProgress());
        assertTrue(message.isAutoFade());
        assertEquals(flags, message.getFlags());
    }
}

