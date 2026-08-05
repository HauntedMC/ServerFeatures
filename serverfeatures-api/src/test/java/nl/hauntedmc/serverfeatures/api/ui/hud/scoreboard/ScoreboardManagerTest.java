package nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreboardManagerTest {

    @Test
    void skipsTeamWorkWhenEntryAlreadyHasTargetColor() {
        Scoreboard board = mock(Scoreboard.class);
        Team current = mock(Team.class);
        when(board.getEntryTeam("Remy")).thenReturn(current);
        when(current.getName()).thenReturn("sf_glow_aqua");

        ScoreboardManager.moveEntryToGlowTeam(board, "Remy", NamedTextColor.AQUA, "test");

        verify(board, never()).getTeam("sf_glow_aqua");
        verify(board, never()).getTeams();
        verify(current, never()).removeEntry("Remy");
        verify(current, never()).addEntry("Remy");
    }

    @Test
    void movesEntryDirectlyFromItsCurrentGlowTeam() {
        Scoreboard board = mock(Scoreboard.class);
        Team current = mock(Team.class);
        Team target = mock(Team.class);
        when(board.getEntryTeam("Remy")).thenReturn(current);
        when(current.getName()).thenReturn("sf_glow_red");
        when(board.getTeam("sf_glow_aqua")).thenReturn(target);

        ScoreboardManager.moveEntryToGlowTeam(board, "Remy", NamedTextColor.AQUA, "test");

        verify(current).removeEntry("Remy");
        verify(target).addEntry("Remy");
        verify(board, never()).getTeams();
    }

    @Test
    void lazilyCreatesAndConfiguresMissingTargetTeam() {
        Scoreboard board = mock(Scoreboard.class);
        Team target = mock(Team.class);
        when(board.getTeam("sf_glow_aqua")).thenReturn(null);
        when(board.registerNewTeam("sf_glow_aqua")).thenReturn(target);

        ScoreboardManager.moveEntryToGlowTeam(board, "Remy", NamedTextColor.AQUA, "test");

        verify(target).color(NamedTextColor.AQUA);
        verify(target).setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        verify(target).addEntry("Remy");
        verify(board, never()).getTeams();
    }

    @Test
    void leavesEntriesOwnedByAnotherTeamUntouched() {
        Scoreboard board = mock(Scoreboard.class);
        Team current = mock(Team.class);
        Logger logger = mock(Logger.class);
        when(board.getEntryTeam("Remy")).thenReturn(current);
        when(current.getName()).thenReturn("another_plugin");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(logger);

            ScoreboardManager.moveEntryToGlowTeam(board, "Remy", NamedTextColor.AQUA, "test");
            ScoreboardManager.moveEntryToGlowTeam(board, "Remy", NamedTextColor.AQUA, "test");
        }

        verify(logger, times(1)).warning(contains("another_plugin"));
        verify(current, never()).removeEntry("Remy");
        verify(board, never()).getTeam("sf_glow_aqua");
        verify(board, never()).getTeams();
    }
}
