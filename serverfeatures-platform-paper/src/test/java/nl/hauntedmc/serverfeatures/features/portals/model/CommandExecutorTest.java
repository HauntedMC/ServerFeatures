package nl.hauntedmc.serverfeatures.features.portals.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandExecutorTest {

    @Test
    void fromStringParsesAliasesAndFallsBackToDefault() {
        assertEquals(CommandExecutor.PLAYER, CommandExecutor.fromString("player", CommandExecutor.CONSOLE));
        assertEquals(CommandExecutor.PLAYER, CommandExecutor.fromString("p", CommandExecutor.CONSOLE));
        assertEquals(CommandExecutor.CONSOLE, CommandExecutor.fromString("server", CommandExecutor.PLAYER));
        assertEquals(CommandExecutor.PLAYER, CommandExecutor.fromString("unknown", CommandExecutor.PLAYER));
        assertEquals(CommandExecutor.CONSOLE, CommandExecutor.fromString(null, CommandExecutor.CONSOLE));
    }
}

