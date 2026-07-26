package nl.hauntedmc.serverfeatures.features.portals.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalDefinitionTest {

    @Test
    void defaultsAndMutatorsWorkForAllModes() {
        PortalDefinition def = new PortalDefinition("spawn");

        assertEquals("spawn", def.id());
        assertEquals(PortalMode.TELEPORT, def.mode());
        assertEquals(CommandExecutor.CONSOLE, def.executor());

        def.setMode(PortalMode.COMMAND);
        def.setCommand("warp spawn", null);
        assertEquals(PortalMode.COMMAND, def.mode());
        assertEquals("warp spawn", def.command().orElseThrow());
        assertEquals(CommandExecutor.CONSOLE, def.executor());

        def.setMode(PortalMode.SERVER);
        def.setServerTarget("hub");
        assertEquals("hub", def.serverTarget().orElseThrow());
    }

    @Test
    void clampsSoundAndParticleDelayToNonNegative() {
        PortalDefinition def = new PortalDefinition("x");

        def.setSound(null, -5);
        def.setParticle(null, -2);

        assertEquals(0, def.soundDelay());
        assertEquals(0, def.particleDelay());
        assertTrue(def.sound().isEmpty());
        assertTrue(def.particle().isEmpty());

        def.clearSound();
        def.clearParticle();
        assertTrue(def.sound().isEmpty());
        assertTrue(def.particle().isEmpty());
    }

    @Test
    void supportsExclusiveBlockToggle() {
        PortalDefinition def = new PortalDefinition("x");

        assertTrue(def.exclusiveBlock().isEmpty());
        def.setExclusiveBlock(null);
        assertTrue(def.exclusiveBlock().isEmpty());
        def.clearExclusiveBlock();
        assertTrue(def.exclusiveBlock().isEmpty());
    }
}
