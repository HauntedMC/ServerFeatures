package nl.hauntedmc.serverfeatures.common.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for per-player scoreboards (sidebar), hide‑nametag teams, and glow teams.
 */
public class ScoreboardManager {
    private static final String HIDETAG_PREFIX = "sf_nametag_";
    private static final String GLOW_PREFIX    = "sf_glow_";
    private static final String OBJ_NAME       = "ServerSB";
    private static final String OBJ_CRITERIA   = "dummy";

    private static final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private static final Map<UUID, NamedTextColor> glowColors = new ConcurrentHashMap<>();


    /** Called on PlayerJoinEvent */
    public static void onPlayerJoin(Player player) {
        // Create personal board
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);

        // Ensure hide‑nametag teams for *all* players on this board
        for (Player other : Bukkit.getOnlinePlayers()) {
            String hideTeamName = HIDETAG_PREFIX + other.getUniqueId();
            Team hideTeam = board.getTeam(hideTeamName);
            if (hideTeam == null) {
                hideTeam = board.registerNewTeam(hideTeamName);
                hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            hideTeam.addEntry(other.getName());
        }

        // Pre‑register glow teams on this board
        for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            String glowTeamName = GLOW_PREFIX + color.toString();
            Team glowTeam = board.getTeam(glowTeamName);
            if (glowTeam == null) {
                glowTeam = board.registerNewTeam(glowTeamName);
                glowTeam.color(color);
                glowTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
        }

        // Populate glow membership for existing players
        for (Player other : Bukkit.getOnlinePlayers()) {
            NamedTextColor c = glowColors.getOrDefault(other.getUniqueId(), NamedTextColor.GRAY);
            Team team = board.getTeam(GLOW_PREFIX + c.toString());
            if (team != null) team.addEntry(other.getName());
        }

        // Give this board to the player
        player.setScoreboard(board);

        // Finally, inject *this* player into everyone else's hide & glow teams
        boards.forEach((uuid, otherBoard) -> {
            if (uuid.equals(player.getUniqueId())) return;
            // hide team
            String hideTeamName = HIDETAG_PREFIX + player.getUniqueId();
            Team hide = otherBoard.getTeam(hideTeamName);
            if (hide == null) {
                hide = otherBoard.registerNewTeam(hideTeamName);
                hide.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            hide.addEntry(player.getName());

            NamedTextColor c = glowColors.getOrDefault(player.getUniqueId(), NamedTextColor.GRAY);
            Team glow = otherBoard.getTeam(GLOW_PREFIX + c.toString());
            if (glow != null) glow.addEntry(player.getName());
        });
    }

    /** Called on PlayerQuitEvent */
    public static void onPlayerQuit(Player player) {
        boards.remove(player.getUniqueId());

        boards.values().forEach(board -> {
            String hideTeamName = HIDETAG_PREFIX + player.getUniqueId();
            Team hide = board.getTeam(hideTeamName);

            if (hide != null) {
                hide.removeEntry(player.getName());
                if (hide.getEntries().isEmpty()) hide.unregister();
            }

            glowColors.remove(player.getUniqueId());
            board.getTeams().stream()
                    .filter(t -> t.getName().startsWith(GLOW_PREFIX))
                    .forEach(t -> t.removeEntry(player.getName()));
        });

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Updates this player's sidebar lines.
     *
     * @param player   The target player
     * @param title    Localized title
     * @param newLines New lines
     * @param oldLines Old lines
     */
    public static void updateSidebar(Player player, Component title, List<Component> newLines, List<Component> oldLines) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Objective obj = board.getObjective(OBJ_NAME);

        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, OBJ_CRITERIA, title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.displayName(title);
        }

        for (int i = 0; i < newLines.size(); i++) {
            int scoreValue = newLines.size() - i;
            String lineKey = "line" + i;
            Score score = obj.getScore(lineKey);

            boolean needsUpdate = oldLines == null || i >= oldLines.size() || !oldLines.get(i).equals(newLines.get(i));
            if (needsUpdate) {
                score.setScore(scoreValue);
                score.customName(newLines.get(i));
                score.numberFormat(NumberFormat.blank());
            }
        }

        if (oldLines != null && oldLines.size() > newLines.size()) {
            for (int i = newLines.size(); i < oldLines.size(); i++) {
                String lineKey = "line" + i;
                board.resetScores(lineKey);
            }
        }

    }

    public static void removeSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Objective obj = board.getObjective(OBJ_NAME);
        if (obj != null) {
            obj.unregister();
        }
    }

    /**
     * Apply glow color for everyone: moves <player> into the chosen glow team
     * on every personal scoreboard, and toggles their glowing flag.
     */
    public static void setGlow(Player player, NamedTextColor color) {
        glowColors.put(player.getUniqueId(), color);

        boards.values().forEach(board -> {
            board.getTeams().stream()
                    .filter(t -> t.getName().startsWith(GLOW_PREFIX))
                    .forEach(t -> t.removeEntry(player.getName()));

            Team team = board.getTeam(GLOW_PREFIX + color.toString());
            if (team != null) team.addEntry(player.getName());
        });

        player.setGlowing(true);
    }

    /** Disable glow (fall back to GRAY) */
    public static void removeGlow(Player player) {
        setGlow(player, NamedTextColor.GRAY);
        player.setGlowing(false);
    }
}
