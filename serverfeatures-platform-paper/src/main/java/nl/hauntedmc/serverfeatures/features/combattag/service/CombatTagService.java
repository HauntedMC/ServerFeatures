package nl.hauntedmc.serverfeatures.features.combattag.service;

import nl.hauntedmc.serverfeatures.api.combat.CombatOpponent;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagApi;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagSnapshot;
import nl.hauntedmc.serverfeatures.api.combat.CombatUntagReason;
import nl.hauntedmc.serverfeatures.features.combattag.CombatTag;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CombatTagService implements CombatTagApi {

    private static final double LOGOUT_DAMAGE = 10_000.0D;
    private static final Pattern COMMAND_PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([a-z_]+)}");

    private final CombatTag feature;
    private final CombatTagSettings settings;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final BooleanSupplier primaryThread;
    private final BooleanSupplier serverStopping;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> restrictionFeedback = new HashMap<>();
    private final Map<UUID, ActionBarFrame> actionBarFrames = new HashMap<>();
    private final long durationNanos;

    public CombatTagService(CombatTag feature, CombatTagSettings settings) {
        this(
                feature,
                settings,
                System::nanoTime,
                Clock.systemUTC(),
                Bukkit::isPrimaryThread,
                Bukkit::isStopping
        );
    }

    CombatTagService(
            CombatTag feature,
            CombatTagSettings settings,
            LongSupplier nanoTime,
            Clock clock
    ) {
        this(feature, settings, nanoTime, clock, () -> true, () -> false);
    }

    CombatTagService(
            CombatTag feature,
            CombatTagSettings settings,
            LongSupplier nanoTime,
            Clock clock,
            BooleanSupplier primaryThread
    ) {
        this(feature, settings, nanoTime, clock, primaryThread, () -> false);
    }

    CombatTagService(
            CombatTag feature,
            CombatTagSettings settings,
            LongSupplier nanoTime,
            Clock clock,
            BooleanSupplier primaryThread,
            BooleanSupplier serverStopping
    ) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.nanoTime = java.util.Objects.requireNonNull(nanoTime, "nanoTime");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.primaryThread = java.util.Objects.requireNonNull(primaryThread, "primaryThread");
        this.serverStopping = java.util.Objects.requireNonNull(serverStopping, "serverStopping");
        this.durationNanos = Duration.ofSeconds(settings.tagging().durationSeconds()).toNanos();
    }

    @Override
    public boolean isTagged(UUID playerId) {
        return getTag(playerId).isPresent();
    }

    @Override
    public Optional<CombatTagSnapshot> getTag(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Session session = sessions.get(playerId);
        if (session == null) {
            return Optional.empty();
        }
        long remainingNanos = session.expiresAtNanos() - nanoTime.getAsLong();
        if (remainingNanos <= 0L) {
            return Optional.empty();
        }
        return Optional.of(snapshot(session, remainingNanos));
    }

    @Override
    public CombatTagResult tag(Player player, Entity opponent, CombatTagReason reason) {
        requirePrimaryThread();
        if (player == null || opponent == null || reason == null) {
            return CombatTagResult.INVALID;
        }
        return tag(
                player,
                CombatSourceResolver.opponent(opponent),
                opponent.getUniqueId(),
                reason,
                true
        );
    }

    public CombatTagResult tagIncoming(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason
    ) {
        requirePrimaryThread();
        return tag(player, opponent, damageSourceId, reason, true);
    }

    public CombatTagResult tagOutgoing(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason
    ) {
        requirePrimaryThread();
        return tag(player, opponent, damageSourceId, reason, false);
    }

    private CombatTagResult tag(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason,
            boolean incomingDamage
    ) {
        if (player == null || opponent == null || damageSourceId == null || reason == null) {
            return CombatTagResult.INVALID;
        }
        if (player.hasPermission(CombatTag.BYPASS_PERMISSION)) {
            untagInternal(player, CombatUntagReason.ADMINISTRATIVE, false);
            return CombatTagResult.BYPASSED;
        }
        if (!settings.tagging().worlds().allows(player.getWorld())) {
            untagInternal(player, CombatUntagReason.WORLD_CHANGE, false);
            return CombatTagResult.WORLD_BLOCKED;
        }
        if (!settings.tagging().allowSelfCombat()
                && player.getUniqueId().equals(opponent.uniqueId())) {
            return CombatTagResult.INVALID;
        }

        UUID playerId = player.getUniqueId();
        long nowNanos = nanoTime.getAsLong();
        Instant now = clock.instant();
        Session previous = sessions.get(playerId);
        boolean retagged = previous != null && previous.expiresAtNanos() > nowNanos;
        if (!retagged) {
            restrictionFeedback.remove(playerId);
        }

        CombatOpponent logoutOpponent = incomingDamage
                ? opponent
                : retagged ? previous.logoutOpponent() : null;
        UUID logoutDamageSourceId = incomingDamage
                ? damageSourceId
                : retagged ? previous.logoutDamageSourceId() : null;
        CombatTagReason logoutReason = incomingDamage
                ? reason
                : retagged ? previous.logoutReason() : null;

        Session session = new Session(
                playerId,
                opponent,
                damageSourceId,
                reason,
                logoutOpponent,
                logoutDamageSourceId,
                logoutReason,
                now,
                now.plusSeconds(settings.tagging().durationSeconds()),
                saturatedAdd(nowNanos, durationNanos)
        );
        sessions.put(playerId, session);
        actionBarFrames.remove(playerId);

        if (!retagged && settings.display().chatEnter()) {
            feature.sendMessage(
                    player,
                    "combattag.enter",
                    Map.of("opponent", opponent.displayName())
            );
        }
        if (settings.display().actionBar().enabled()) {
            showActionBar(player, session, durationNanos);
        }
        CombatTagResult result = retagged ? CombatTagResult.RETAGGED : CombatTagResult.TAGGED;
        feature.publishAppliedTag(player, result);
        return result;
    }

    public boolean resetTimer(Player player) {
        requirePrimaryThread();
        if (player == null) {
            return false;
        }
        Session session = activeSession(player.getUniqueId());
        if (session == null) {
            return false;
        }
        long nowNanos = nanoTime.getAsLong();
        Instant now = clock.instant();
        Session reset = new Session(
                session.playerId(),
                session.opponent(),
                session.damageSourceId(),
                session.reason(),
                session.logoutOpponent(),
                session.logoutDamageSourceId(),
                session.logoutReason(),
                now,
                now.plusSeconds(settings.tagging().durationSeconds()),
                saturatedAdd(nowNanos, durationNanos)
        );
        sessions.put(player.getUniqueId(), reset);
        actionBarFrames.remove(player.getUniqueId());
        if (settings.display().actionBar().enabled()) {
            showActionBar(player, reset, durationNanos);
        }
        return true;
    }

    @Override
    public boolean untag(Player player, CombatUntagReason reason) {
        requirePrimaryThread();
        return untagInternal(player, reason, true);
    }

    public boolean untag(Player player, CombatUntagReason reason, boolean notify) {
        requirePrimaryThread();
        return untagInternal(player, reason, notify);
    }

    private boolean untagInternal(Player player, CombatUntagReason reason, boolean notify) {
        if (player == null || reason == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Session removed = sessions.remove(playerId);
        if (removed == null) {
            return false;
        }
        restrictionFeedback.remove(playerId);
        if (notify && player.isOnline() && settings.display().chatExit()) {
            feature.sendMessage(player, "combattag.exit");
        }
        clearActionBar(player);
        return true;
    }

    public void tick() {
        long now = nanoTime.getAsLong();
        for (Session session : sessions.values()) {
            Player player = feature.getPlugin().getServer().getPlayer(session.playerId());
            long remainingNanos = session.expiresAtNanos() - now;
            if (remainingNanos <= 0L) {
                if (sessions.remove(session.playerId(), session) && player != null) {
                    restrictionFeedback.remove(session.playerId());
                    if (settings.display().chatExit()) {
                        feature.sendMessage(player, "combattag.exit");
                    }
                    clearActionBar(player);
                } else {
                    restrictionFeedback.remove(session.playerId());
                    actionBarFrames.remove(session.playerId());
                }
                continue;
            }
            if (player == null) {
                continue;
            }
            if (player.hasPermission(CombatTag.BYPASS_PERMISSION)) {
                if (sessions.remove(session.playerId(), session)) {
                    restrictionFeedback.remove(session.playerId());
                    clearActionBar(player);
                }
                continue;
            }
            if (settings.display().actionBar().enabled()) {
                showActionBar(player, session, remainingNanos);
            }
        }
    }

    public void handlePlayerDeath(Player player) {
        if (settings.lifecycle().clearOnPlayerDeath()) {
            untagInternal(player, CombatUntagReason.PLAYER_DEATH, true);
        }
        if (settings.lifecycle().clearWhenOpponentDies()) {
            clearByOpponent(player.getUniqueId());
        }
    }

    public void handleOpponentDeath(Entity entity) {
        if (settings.lifecycle().clearWhenOpponentDies()) {
            clearByOpponent(entity.getUniqueId());
        }
    }

    public void handleQuit(Player player, boolean kicked) {
        Session session = activeSession(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        restrictionFeedback.remove(player.getUniqueId());
        actionBarFrames.remove(player.getUniqueId());
        if (session == null
                || serverStopping.getAsBoolean()
                || player.hasPermission(CombatTag.BYPASS_PERMISSION)
                || (kicked && !settings.logout().punishKickedPlayers())) {
            return;
        }
        punishLogout(player, session);
    }

    public void handleWorldChange(Player player) {
        if (!settings.tagging().worlds().allows(player.getWorld())) {
            untagInternal(player, CombatUntagReason.WORLD_CHANGE, true);
        }
    }

    public void sendTeleportBlocked(Player player, boolean portal) {
        long now = nanoTime.getAsLong();
        long previous = restrictionFeedback.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE
                && now - previous < settings.feedback().restrictionMessageCooldownNanos()) {
            return;
        }
        restrictionFeedback.put(player.getUniqueId(), now);
        feature.sendMessage(player, portal ? "combattag.portal-blocked" : "combattag.teleport-blocked");
    }

    public Map<UUID, StoredSession> snapshotForReload() {
        long now = nanoTime.getAsLong();
        Map<UUID, StoredSession> snapshot = new LinkedHashMap<>();
        for (Session session : sessions.values()) {
            long remainingNanos = session.expiresAtNanos() - now;
            if (remainingNanos > 0L) {
                snapshot.put(
                        session.playerId(),
                        new StoredSession(
                                session.opponent(),
                                session.damageSourceId(),
                                session.reason(),
                                session.logoutOpponent(),
                                session.logoutDamageSourceId(),
                                session.logoutReason(),
                                remainingNanos
                        )
                );
            }
        }
        return Map.copyOf(snapshot);
    }

    public void restore(Map<UUID, StoredSession> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        long nowNanos = nanoTime.getAsLong();
        Instant now = clock.instant();
        for (Map.Entry<UUID, StoredSession> entry : snapshot.entrySet()) {
            Player player = feature.getPlugin().getServer().getPlayer(entry.getKey());
            StoredSession stored = entry.getValue();
            if (player == null
                    || stored == null
                    || stored.remainingNanos() <= 0L
                    || player.hasPermission(CombatTag.BYPASS_PERMISSION)
                    || !settings.tagging().worlds().allows(player.getWorld())) {
                continue;
            }
            long remaining = Math.min(stored.remainingNanos(), durationNanos);
            sessions.put(
                    player.getUniqueId(),
                    new Session(
                            player.getUniqueId(),
                            stored.opponent(),
                            stored.damageSourceId(),
                            stored.reason(),
                            stored.logoutOpponent(),
                            stored.logoutDamageSourceId(),
                            stored.logoutReason(),
                            now,
                            now.plusNanos(remaining),
                            saturatedAdd(nowNanos, remaining)
                    )
            );
        }
    }

    public void shutdown() {
        for (UUID playerId : actionBarFrames.keySet()) {
            Player player = feature.getPlugin().getServer().getPlayer(playerId);
            if (player != null) {
                feature.clearActionBar(player);
            }
        }
        sessions.clear();
        restrictionFeedback.clear();
        actionBarFrames.clear();
    }

    Session activeSession(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return null;
        }
        if (session.expiresAtNanos() <= nanoTime.getAsLong()) {
            sessions.remove(playerId, session);
            restrictionFeedback.remove(playerId);
            actionBarFrames.remove(playerId);
            return null;
        }
        return session;
    }

    private void clearByOpponent(UUID opponentId) {
        for (Session session : sessions.values()) {
            boolean currentOpponentDied = session.opponent().uniqueId().equals(opponentId);
            boolean logoutOpponentDied = session.logoutOpponent() != null
                    && session.logoutOpponent().uniqueId().equals(opponentId);
            if (!currentOpponentDied && !logoutOpponentDied) {
                continue;
            }

            if (currentOpponentDied && session.logoutOpponent() != null && !logoutOpponentDied) {
                Session fallback = new Session(
                        session.playerId(),
                        session.logoutOpponent(),
                        session.logoutDamageSourceId(),
                        session.logoutReason(),
                        session.logoutOpponent(),
                        session.logoutDamageSourceId(),
                        session.logoutReason(),
                        session.taggedAt(),
                        session.expiresAt(),
                        session.expiresAtNanos()
                );
                if (sessions.replace(session.playerId(), session, fallback)) {
                    actionBarFrames.remove(session.playerId());
                }
                continue;
            }

            if (!currentOpponentDied) {
                Session withoutLogoutAttribution = new Session(
                        session.playerId(),
                        session.opponent(),
                        session.damageSourceId(),
                        session.reason(),
                        null,
                        null,
                        null,
                        session.taggedAt(),
                        session.expiresAt(),
                        session.expiresAtNanos()
                );
                sessions.replace(session.playerId(), session, withoutLogoutAttribution);
                continue;
            }

            Player player = feature.getPlugin().getServer().getPlayer(session.playerId());
            if (sessions.remove(session.playerId(), session)) {
                restrictionFeedback.remove(session.playerId());
                if (player != null) {
                    if (settings.display().chatExit()) {
                        feature.sendMessage(player, "combattag.exit");
                    }
                    clearActionBar(player);
                } else {
                    actionBarFrames.remove(session.playerId());
                }
            }
        }
    }

    private void showActionBar(Player player, Session session, long remainingNanos) {
        CombatTagSettings.ActionBarSettings actionBar = settings.display().actionBar();
        double fraction = Math.min(1.0D, Math.max(0.0D, (double) remainingNanos / durationNanos));
        int filled = Math.min(
                actionBar.segments(),
                Math.max(0, (int) Math.ceil(fraction * actionBar.segments()))
        );
        long seconds = Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
        ActionBarFrame frame = new ActionBarFrame(
                seconds,
                filled,
                session.opponent().uniqueId(),
                session.opponent().displayName()
        );
        if (frame.equals(actionBarFrames.get(player.getUniqueId()))) {
            return;
        }

        String filledBar = actionBar.filledSymbol().repeat(filled);
        String emptyBar = actionBar.emptySymbol().repeat(actionBar.segments() - filled);
        feature.sendActionBar(
                player,
                "combattag.action-bar",
                Map.of(
                        "filled", filledBar,
                        "empty", emptyBar,
                        "seconds", Long.toString(seconds),
                        "opponent", session.opponent().displayName()
                )
        );
        actionBarFrames.put(player.getUniqueId(), frame);
    }

    private void clearActionBar(Player player) {
        if (actionBarFrames.remove(player.getUniqueId()) != null) {
            feature.clearActionBar(player);
        }
    }

    private void punishLogout(Player player, Session session) {
        CombatTagSettings.LogoutSettings logout = settings.logout();
        if (!logout.enabled()) {
            return;
        }

        CombatOpponent logoutOpponent = session.logoutOpponent();
        UUID logoutDamageSourceId = session.logoutDamageSourceId();
        Entity attacker = logoutDamageSourceId == null ? null : Bukkit.getEntity(logoutDamageSourceId);
        if (attacker == null && logoutOpponent != null) {
            attacker = Bukkit.getEntity(logoutOpponent.uniqueId());
        }
        if (attacker != null && attacker.getUniqueId().equals(player.getUniqueId())) {
            attacker = null;
        }

        Map<String, String> placeholders = placeholders(player, logoutOpponent, attacker);
        if (logout.killPlayer()) {
            punishWithDeath(player, attacker);
        }
        if (logout.broadcast()) {
            feature.broadcastMessage(
                    logoutOpponent == null
                            ? "combattag.logout-broadcast-unknown"
                            : "combattag.logout-broadcast",
                    placeholders
            );
        }
        for (String command : logout.commands()) {
            try {
                boolean handled = feature.getPlugin().getServer().dispatchCommand(
                        feature.getPlugin().getServer().getConsoleSender(),
                        replaceCommandPlaceholders(command, placeholders)
                );
                if (!handled) {
                    feature.reportFailure(
                            "CombatTag logout command was not recognized: " + command,
                            null
                    );
                }
            } catch (RuntimeException exception) {
                feature.reportFailure("Could not execute CombatTag logout command: " + command, exception);
            }
        }
    }

    private void punishWithDeath(Player player, Entity attacker) {
        try {
            DamageSource.Builder builder = DamageSource.builder(DamageType.GENERIC_KILL);
            if (attacker != null) {
                builder.withCausingEntity(attacker).withDirectEntity(attacker);
            }
            player.damage(LOGOUT_DAMAGE, builder.build());
        } catch (RuntimeException exception) {
            feature.reportFailure("Could not apply attributed CombatTag logout damage", exception);
        }

        if (!player.isDead() && player.getHealth() > 0.0D) {
            try {
                player.setHealth(0.0D);
            } catch (RuntimeException exception) {
                feature.reportFailure("Could not apply CombatTag fallback logout death", exception);
            }
        }
    }

    private static Map<String, String> placeholders(
            Player player,
            CombatOpponent logoutOpponent,
            Entity attacker
    ) {
        Location location = player.getLocation();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("attacker", logoutOpponent == null ? "" : logoutOpponent.displayName());
        placeholders.put(
                "attacker_uuid",
                logoutOpponent == null ? "" : logoutOpponent.uniqueId().toString()
        );
        placeholders.put(
                "attacker_type",
                logoutOpponent == null ? "UNKNOWN" : logoutOpponent.entityType().name()
        );
        placeholders.put("attacker_known", Boolean.toString(logoutOpponent != null));
        placeholders.put("world", location.getWorld() == null ? "unknown" : location.getWorld().getName());
        placeholders.put("x", Integer.toString(location.getBlockX()));
        placeholders.put("y", Integer.toString(location.getBlockY()));
        placeholders.put("z", Integer.toString(location.getBlockZ()));
        placeholders.put("source_available", Boolean.toString(attacker != null));
        return Map.copyOf(placeholders);
    }

    static String replaceCommandPlaceholders(
            String command,
            Map<String, String> placeholders
    ) {
        Matcher matcher = COMMAND_PLACEHOLDER_PATTERN.matcher(command);
        StringBuilder result = new StringBuilder(command.length());
        while (matcher.find()) {
            String replacement = placeholders.get(matcher.group(1));
            if (replacement == null) {
                replacement = matcher.group();
            } else {
                replacement = sanitizeCommandValue(replacement);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String sanitizeCommandValue(String value) {
        StringBuilder sanitized = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isISOControl(character)) {
                continue;
            }
            if (sanitized == null) {
                sanitized = new StringBuilder(value);
            }
            sanitized.setCharAt(index, ' ');
        }
        return sanitized == null ? value : sanitized.toString();
    }

    private CombatTagSnapshot snapshot(Session session, long remainingNanos) {
        return new CombatTagSnapshot(
                session.playerId(),
                session.opponent(),
                session.reason(),
                session.taggedAt(),
                session.expiresAt(),
                Duration.ofNanos(remainingNanos)
        );
    }

    private void requirePrimaryThread() {
        if (!primaryThread.getAsBoolean()) {
            throw new IllegalStateException("CombatTag write operations must run on the server thread");
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    record Session(
            UUID playerId,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason,
            CombatOpponent logoutOpponent,
            UUID logoutDamageSourceId,
            CombatTagReason logoutReason,
            Instant taggedAt,
            Instant expiresAt,
            long expiresAtNanos
    ) {
        Session {
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(opponent, "opponent");
            java.util.Objects.requireNonNull(damageSourceId, "damageSourceId");
            java.util.Objects.requireNonNull(reason, "reason");
            java.util.Objects.requireNonNull(taggedAt, "taggedAt");
            java.util.Objects.requireNonNull(expiresAt, "expiresAt");
            boolean hasLogoutAttribution = logoutOpponent != null;
            if (hasLogoutAttribution != (logoutDamageSourceId != null)
                    || hasLogoutAttribution != (logoutReason != null)) {
                throw new IllegalArgumentException(
                        "Logout opponent, damage source, and reason must all be present or absent"
                );
            }
        }
    }

    public record StoredSession(
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason,
            CombatOpponent logoutOpponent,
            UUID logoutDamageSourceId,
            CombatTagReason logoutReason,
            long remainingNanos
    ) {
        public StoredSession {
            java.util.Objects.requireNonNull(opponent, "opponent");
            java.util.Objects.requireNonNull(damageSourceId, "damageSourceId");
            java.util.Objects.requireNonNull(reason, "reason");
            boolean hasLogoutAttribution = logoutOpponent != null;
            if (hasLogoutAttribution != (logoutDamageSourceId != null)
                    || hasLogoutAttribution != (logoutReason != null)) {
                throw new IllegalArgumentException(
                        "Logout opponent, damage source, and reason must all be present or absent"
                );
            }
            if (remainingNanos < 0L) {
                remainingNanos = 0L;
            }
        }
    }

    private record ActionBarFrame(
            long seconds,
            int filledSegments,
            UUID opponentId,
            String opponentName
    ) {
    }
}
