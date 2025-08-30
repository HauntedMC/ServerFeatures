package nl.hauntedmc.serverfeatures.common.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Central manager for per-player scoreboards (sidebar) and glow teams.
 * Enforces one-team-per-entry-per-board. Nametags are hidden via the glow team itself.
 */
public class ScoreboardManager {

    private static final String GLOW_PREFIX  = "sf_glow_";
    private static final String OBJ_NAME     = "ServerSB";
    private static final String OBJ_CRITERIA = "dummy";

    private static final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private static final Map<UUID, NamedTextColor> glowColors = new ConcurrentHashMap<>();

    /** Called on PlayerJoinEvent */
    public static void onPlayerJoin(Player player) {
        // Create personal board
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);

        // Purge any legacy per-player nametag teams (old builds)
        purgeLegacyHideTeams(board);

        // Ensure all glow teams exist on this board
        ensureGlowTeamsRegistered(board);

        // Put current online players into their glow team on THIS board
        for (Player other : Bukkit.getOnlinePlayers()) {
            NamedTextColor c = glowColors.getOrDefault(other.getUniqueId(), NamedTextColor.GRAY);
            moveEntryToGlowTeam(board, other.getName(), c, "populate on " + player.getName() + "'s board");
        }

        // Give this board to the joining player
        player.setScoreboard(board);

        // Now inject the joining player into EVERYONE ELSE'S boards
        NamedTextColor myColor = glowColors.getOrDefault(player.getUniqueId(), NamedTextColor.GRAY);
        boards.forEach((uuid, otherBoard) -> {
            if (uuid.equals(player.getUniqueId())) return;
            ensureGlowTeamsRegistered(otherBoard);
            purgeLegacyHideTeams(otherBoard);
            moveEntryToGlowTeam(otherBoard, player.getName(), myColor, "inject into " + getName(uuid) + "'s board");
        });
    }


    /** Called on PlayerQuitEvent */
    public static void onPlayerQuit(Player player) {
        internalQuitCleanup(player, /*resetSidebar=*/false);
    }

    /** Call during plugin enable to attach personal boards for players already online. */
    public static void initializeOnlinePlayers(Logger logger) {
        int initialized = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                onPlayerJoin(player);
                initialized++;
            } catch (Throwable t) {
                if (logger != null) {
                    logger.info("Failed to initialize scoreboard for " + player.getName() + ": " + t.getMessage());
                }
            }
        }
        if (initialized > 0) {
            if (logger != null) {
                logger.info("Initialized scoreboards for " + initialized + " already online player(s).");
            }
        }
    }

    /**
     * Force-run quit cleanup for every online player.
     * Safe to call from onDisable(); also clears internal maps.
     */
    public static void cleanupOnlinePlayers(Logger logger) {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            try {
                internalQuitCleanup(p, /*resetSidebar=*/true);
            } catch (Throwable t) {
                if (logger != null) {
                    logger.warning("[ScoreboardManager] Shutdown cleanup failed for " + p.getName() + ": " + t.getMessage());
                }
            }
        }
        boards.clear();
        glowColors.clear();
    }

    /**
     * Shared cleanup used by both real quits and plugin shutdown.
     */
    private static void internalQuitCleanup(Player player, boolean resetSidebar) {
        String entry = player.getName();

        // Remove this player's entry from OUR glow teams on ALL boards (defensive)
        boards.values().forEach(board -> {
            board.getTeams().stream()
                    .filter(t -> isGlowTeam(t.getName()))
                    .filter(t -> t.getEntries().contains(entry))
                    .forEach(t -> t.removeEntry(entry));

            if (resetSidebar) {
                Objective obj = board.getObjective(OBJ_NAME);
                if (obj != null) obj.unregister();
            }
        });

        // Forget color and personal board
        glowColors.remove(player.getUniqueId());
        boards.remove(player.getUniqueId());

        // Return to main board to avoid dangling personal boards
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Updates this player's sidebar lines.
     */
    public static void updateSidebar(Player player, Component title, List<Component> newLines, List<Component> oldLines) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, OBJ_CRITERIA, title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.displayName(title);
        } else {
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
        if (obj != null) obj.unregister();
    }

    /**
     * Apply glow color for everyone: moves <player> into the chosen glow team
     * on every personal scoreboard, and toggles their glowing flag.
     */
    public static void setGlow(Player player, NamedTextColor color) {
        glowColors.put(player.getUniqueId(), color);
        String entry = player.getName();

        boards.values().forEach(board -> {
            ensureGlowTeamsRegistered(board);
            moveEntryToGlowTeam(board, entry, color, "setGlow");
        });

        player.setGlowing(true);
    }

    /** Disable glow (fall back to GRAY) */
    public static void removeGlow(Player player) {
        setGlow(player, NamedTextColor.GRAY);
        player.setGlowing(false);
    }

    /* ---------- helpers ---------- */

    private static void ensureGlowTeamsRegistered(Scoreboard board) {
        for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            String name = glowTeamName(color);
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            // Keep settings consistent
            team.color(color);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
    }

    private static void moveEntryToGlowTeam(Scoreboard board, String entry, NamedTextColor targetColor, String context) {
        // If entry sits in a non-our team on this board, don't fight it — skip to avoid conflicts
        Set<String> otherTeams = board.getTeams().stream()
                .filter(t -> !isGlowTeam(t.getName()))
                .filter(t -> t.getEntries().contains(entry))
                .map(Team::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!otherTeams.isEmpty()) {
            warn("Entry {} already in non-glow team(s) {} on this board during {}. Skipping our team change.",
                    entry, otherTeams, context);
            return;
        }

        // Remove from ALL our glow teams first — only if present (defensive)
        board.getTeams().stream()
                .filter(t -> isGlowTeam(t.getName()))
                .filter(t -> t.getEntries().contains(entry))
                .forEach(t -> t.removeEntry(entry));

        // Add to the selected glow team
        String teamName = glowTeamName(targetColor);
        Team team = board.getTeam(teamName);
        if (team == null) {
            warn("Glow team {} missing on a board during {} for {}", teamName, context, entry);
            return;
        }
        if (!team.getEntries().contains(entry)) {
            team.addEntry(entry);
        }

        // After: verify exactly one of our glow teams contains the entry
        int afterGlow = (int) board.getTeams().stream()
                .filter(t -> isGlowTeam(t.getName()))
                .filter(t -> t.getEntries().contains(entry))
                .count();
        if (afterGlow != 1) {
            warn("After {}, {} is in {} glow team(s) on a board. Teams now: {}", context, entry, afterGlow,
                    teamsContaining(board, entry));
        }
    }

    private static String glowTeamName(NamedTextColor color) {
        return GLOW_PREFIX + color.toString();
    }

    private static boolean isGlowTeam(String teamName) {
        return teamName != null && teamName.startsWith(GLOW_PREFIX);
    }

    private static void purgeLegacyHideTeams(Scoreboard board) {
        // Remove any old "sf_nametag_*" teams (from legacy versions)
        List<Team> legacy = new ArrayList<>();
        for (Team t : board.getTeams()) {
            String n = t.getName();
            if (n.startsWith("sf_nametag_")) {
                legacy.add(t);
            }
        }
        for (Team t : legacy) {
            for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
            t.unregister();
        }
    }

    private static Set<String> teamsContaining(Scoreboard board, String entry) {
        return board.getTeams().stream()
                .filter(t -> t.getEntries().contains(entry))
                .map(Team::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void warn(String msg, Object... args) {
        String out = msg;
        for (Object a : args) {
            out = out.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
        }
        Bukkit.getLogger().warning("[ScoreboardManager] " + out);
    }

    private static String getName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString();
    }
}
