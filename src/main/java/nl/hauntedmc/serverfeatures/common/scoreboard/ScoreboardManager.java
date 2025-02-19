package nl.hauntedmc.serverfeatures.common.scoreboard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

/**
 * Manages scoreboard teams to apply custom effects like hiding name tags and applying glow effects.
 */
public class ScoreboardManager {
    private static final String PREFIX = "sf_nametag_";

    /**
     * Check if a player is part of a dedicated team.
     *
     * @param player The player to check the team.
     * @return true if the player is in a valid team, false otherwise.
     */
    public static boolean hasValidTeam(@NotNull Player player) {
        return getPlayerTeam(player).isPresent();
    }

    /**
     * Ensures a player is part of a dedicated team that hides their name tag.
     *
     * @param player The player to assign to the team.
     * @return true if the player was successfully added to a team, false otherwise.
     */
    public static boolean addPlayerToTeam(@NotNull Player player) {
        return getOrCreateTeam(player).map(team -> {
            team.addEntry(player.getName());
            return true;
        }).orElse(false);
    }

    /**
     * Removes a player from their team.
     *
     * @param player The player to remove.
     * @return true if the player was removed, false otherwise.
     */
    public static boolean removePlayerFromTeam(@NotNull Player player) {
        return getPlayerTeam(player).map(team -> {
            team.removeEntry(player.getName());
            if (team.getSize() == 0) {
                team.unregister();
            }
            return true;
        }).orElse(false);
    }

    /**
     * Checks if a player is in a nametag-hiding team.
     *
     * @param player The player to check.
     * @return An Optional containing the team if the player is part of one, otherwise empty.
     */
    @NotNull
    public static Optional<Team> getPlayerTeam(@NotNull Player player) {
        return Optional.ofNullable(Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + player.getUniqueId()));
    }

    /**
     * Updates a team option for a player's team if it exists.
     *
     * @param player The player whose team should be modified.
     * @param option The team option to modify.
     * @param status The new status to apply.
     * @return true if the team option was successfully updated, false otherwise.
     */
    public static boolean updateTeamOption(@NotNull Player player, @NotNull Team.Option option, @NotNull Team.OptionStatus status) {
        return getPlayerTeam(player).map(team -> {
            team.setOption(option, status);
            return true;
        }).orElse(false);
    }

    /**
     * Retrieves or creates a team for a player.
     * Ensures the team exists and has the correct settings applied.
     *
     * @param player The player whose team should be retrieved or created.
     * @return An Optional containing the player's team if created or found.
     */
    @NotNull
    private static Optional<Team> getOrCreateTeam(@NotNull Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = PREFIX + player.getUniqueId();
        Team team = board.getTeam(teamName);

        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        return Optional.of(team);
    }

    /**
     * Sets the team color for a player's nametag.
     *
     * @param player The player whose team color should be set.
     * @param color The NamedTextColor to apply.
     * @return true if the color was successfully set, false otherwise.
     */
    public static boolean setTeamColor(@NotNull Player player, @NotNull NamedTextColor color) {
        return getOrCreateTeam(player).map(team -> {
            team.color(color);
            return true;
        }).orElse(false);
    }

    /**
     * Clears all plugin-managed teams from the scoreboard.
     */
    public static void clearAllTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        board.getTeams().stream()
                .filter(team -> team.getName().startsWith(PREFIX))
                .forEach(Team::unregister);
    }
}
