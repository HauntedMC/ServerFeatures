package nl.hauntedmc.serverfeatures.api.ui.world.display.options;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VisualOptionsTest {

    @Test
    void builderAppliesOverridesAndFallbacks() {
        VisualOptions opts = VisualOptions.builder()
                .edgeMaterial(Material.GLASS)
                .cornerMaterial(Material.STONE)
                .namedPointMaterial("pos1", Material.DIAMOND_BLOCK)
                .edgeGlow(NamedTextColor.RED)
                .cornerGlow(NamedTextColor.BLUE)
                .namedPointGlow("pos1", NamedTextColor.GREEN)
                .edgeScale(-10f)
                .cornerScale(5f)
                .edgeStepBlocks(0.1d)
                .labelsEnabled(false)
                .labelYOffset(2.0d)
                .labelScale(0.1f)
                .labelTextStrategy((k, i) -> k + "-" + i)
                .build();

        assertEquals(Material.GLASS, opts.edgeMaterial());
        assertEquals(Material.STONE, opts.cornerMaterial());
        assertEquals(Material.DIAMOND_BLOCK, opts.materialForNamedPoint("pos1"));
        assertEquals(Material.STONE, opts.materialForNamedPoint("other"));
        assertEquals(NamedTextColor.RED, opts.edgeGlow());
        assertEquals(NamedTextColor.BLUE, opts.cornerGlow());
        assertEquals(NamedTextColor.GREEN, opts.glowForNamedPoint("pos1"));
        assertEquals(NamedTextColor.BLUE, opts.glowForNamedPoint("other"));
        assertEquals(0.01f, opts.edgeScale());
        assertEquals(1.0f, opts.cornerScale());
        assertEquals(0.25d, opts.edgeStepBlocks());
        assertEquals(false, opts.labelEnabled());
        assertEquals(2.0d, opts.labelYOffset());
        assertEquals(0.25f, opts.labelScale());
        assertEquals("pos1-4", opts.labelTextStrategy().labelFor("pos1", 4));
    }

    @Test
    void defaultLabelStrategyUsesKey() {
        VisualOptions opts = VisualOptions.builder().build();
        assertNotNull(opts.labelTextStrategy());
        assertEquals("x", opts.labelTextStrategy().labelFor("x", 0));
    }
}
