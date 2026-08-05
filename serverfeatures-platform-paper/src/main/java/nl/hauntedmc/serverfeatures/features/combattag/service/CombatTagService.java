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
import java.util.function.LongSupplier;

public final class CombatTagService implements CombatTagApi {

    private final CombatTag feature;
    private final CombatTagSettings settings;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> restrictionFeedback = new HashMap<>();
    private final long durationNanos;

    public CombatTagService(CombatTag feature, CombatTagSettings settings) {
        this(feature, settings, System::nanoTime, Clock.systemUTC());
    }

    CombatTagService(
            CombatTag feature,
            CombatTagSettings settings,
            LongSupplier nanoTime,
            Clock clock
    ) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.nanoTime = java.util.Objects.requireNonNull(nanoTime, "nanoTime");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
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
        if (player == null || opponent == null || reason == null) {
            return CombatTagResult.INVALID;
        }
        return tagIncoming(
                player,
                CombatSourceResolver.opponent(opponent),
                opponent.getUniqueId(),
                reason
        );
    }

    public CombatTagResult tagIncoming(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason
    ) {
        return tag(player, opponent, damageSourceId, reason, true);
    }

    public CombatTagResult tagOutgoing(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason
    ) {
        return tag(player, opponent, damageSourceId, reason, false);
    }

    private CombatTagResult tag(
            Player player,
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason,
            boolean replaceLogoutAttribution
    ) {
        if (player == null || opponent == null || damageSourceId == null || reason == null) {
            return CombatTagResult.INVALID;
        }
        if (player.hasPermission(CombatTag.BYPASS_PERMISSION)) {
            untag(player, CombatUntagReason.ADMINISTRATIVE, false);
            return CombatTagResult.BYPASSED;
        }
        if (!settings.tagging().worlds().allows(player.getWorld())) {
            untag(player, CombatUntagReason.WORLD_CHANGE, false);
            return CombatTagResult.WORLD_BLOCKED;
        }
        if (!settings.tagging().allowSelfCombat()
                && player.getUniqueId().equals(opponent.uniqueId())) {
            return CombatTagResult.INVALID;
        }

        long nowNanos = nanoTime.getAsLong();
        Instant now = clock.instant();
        Session previous = sessions.get(player.getUniqueId());
        boolean retagged = previous != null && previous.expiresAtNanos() > nowNanos;
        CombatOpponent logoutOpponent = opponent;
        UUID logoutDamageSourceId = damageSourceId;
        if (retagged && !replaceLogoutAttribution) {
            logoutOpponent = previous.logoutOpponent();
            logoutDamageSourceId = previous.logoutDamageSourceId();
        }

        Session session = new Session(
                player.getUniqueId(),
                opponent,
                damageSourceId,
                reason,
                logoutOpponent,
                logoutDamageSourceId,
                now,
                now.plusSeconds(settings.tagging().durationSeconds()),
                saturatedAdd(nowNanos, durationNanos)
        );
        sessions.put(player.getUniqueId(), session);
        if (!retagged && settings.display().chatEnter()) {
            feature.sendMessage(
                    player,
                    "combattag.enter",
                    Map.of("opponent", opponent.displayName())
            );
        }
        return retagged ? CombatTagResult.RETAGGED : CombatTagResult.TAGGED;
    }

    public boolean resetTimer(Player player) {
        if (player == null) {
            return false;
        }
        Session session = activeSession(player.getUniqueId());
        if (session == null) {
            return false;
        }
        long nowNanos = nanoTime.getAsLong();
        Instant now = clock.instant();
        sessions.put(
                player.getUniqueId(),
                new Session(
                        session.playerId(),
                        session.opponent(),
                        session.damageSourceId(),
                        session.reason(),
                        session.logoutOpponent(),
                        session.logoutDamageSourceId(),
                        now,
                        now.plusSeconds(settings.tagging().durationSeconds()),
                        saturatedAdd(nowNanos, durationNanos)
                )
        );
        return true;
    }

    @Override
    public boolean untag(Player player, CombatUntagReason reason) {
        return untag(player, reason, true);
    }

    public boolean untag(Player player, CombatUntagReason reason, boolean notify) {
        if (player == null || reason == null) {
            return false;
        }
        Session removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return false;
        }
        if (notify && player.isOnline() && settings.display().chatExit()) {
            feature.sendMessage(player, "combattag.exit");
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        return true;
    }

    public void tick() {
        long now = nanoTime.getAsLong();
        for (Session session : sessions.values()) {
            Player player = feature.getPlugin().getServer().getPlayer(session.playerId());
            long remainingNanos = session.expiresAtNanos() - now;
            if (remainingNanos <= 0L) {
                if (sessions.remove(session.playerId(), session) && player != null) {
                    if (settings.display().chatExit()) {
                        feature.sendMessage(player, "combattag.exit");
                    }
                    player.sendActionBar(net.kyori.adventure.text.Component.empty());
                }
                continue;
            }
            if (player != null && settings.display().actionBar().enabled()) {
                showActionBar(player, session, remainingNanos);
            }
        }
    }

    public void handlePlayerDeath(Player player) {
        if (settings.lifecycle().clearOnPlayerDeath()) {
            untag(player, CombatUntagReason.PLAYER_DEATH);
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

    public void handleQuit(Player player) {
        Session session = activeSession(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        restrictionFeedback.remove(player.getUniqueId());
        if (session == null || player.hasPermission(CombatTag.BYPASS_PERMISSION)) {
            return;
        }
        punishLogout(player, session);
    }

    public void handleWorldChange(Player player) {
        if (!settings.tagging().worlds().allows(player.getWorld())) {
            untag(player, CombatUntagReason.WORLD_CHANGE);
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
                            now,
                            now.plusNanos(remaining),
                            saturatedAdd(nowNanos, remaining)
                    )
            );
        }
    }

    public void shutdown(boolean preserveForReload) {
        if (!preserveForReload) {
            for (UUID playerId : sessions.keySet()) {
                Player player = feature.getPlugin().getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendActionBar(net.kyori.adventure.text.Component.empty());
                }
            }
        }
        sessions.clear();
        restrictionFeedback.clear();
    }

    Session activeSession(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null || session.expiresAtNanos() <= nanoTime.getAsLong()) {
            return null;
        }
        return session;
    }

    private void clearByOpponent(UUID opponentId) {
        for (Session session : sessions.values()) {
            if (!session.opponent().uniqueId().equals(opponentId)) {
                continue;
            }
            Player player = feature.getPlugin().getServer().getPlayer(session.playerId());
            if (sessions.remove(session.playerId(), session) && player != null) {
                if (settings.display().chatExit()) {
                    feature.sendMessage(player, "combattag.exit");
                }
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
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
        String filledBar = actionBar.filledSymbol().repeat(filled);
        String emptyBar = actionBar.emptySymbol().repeat(actionBar.segments() - filled);
        long seconds = Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
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
    }

    private void punishLogout(Player player, Session session) {
        CombatTagSettings.LogoutSettings logout = settings.logout();
        if (!logout.enabled()) {
            return;
        }

        Entity attacker = Bukkit.getEntity(session.logoutDamageSourceId());
        if (attacker == null) {
            attacker = Bukkit.getEntity(session.logoutOpponent().uniqueId());
        }
        Map<String, String> placeholders = placeholders(player, session, attacker);

        if (logout.broadcast()) {
            feature.broadcastMessage("combattag.logout-broadcast", placeholders);
        }
        for (String command : logout.commands()) {
            feature.getPlugin().getServer().dispatchCommand(
                    feature.getPlugin().getServer().getConsoleSender(),
                    replace(command, placeholders)
            );
        }
        if (logout.killPlayer() && !player.isDead()) {
            if (attacker != null && !attacker.getUniqueId().equals(player.getUniqueId())) {
                player.damage(10_000.0D, attacker);
            }
            if (!player.isDead() && player.getHealth() > 0.0D) {
                player.setHealth(0.0D);
            }
        }
    }

    private static Map<String, String> placeholders(Player player, Session session, Entity attacker) {
        Location location = player.getLocation();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("attacker", session.logoutOpponent().displayName());
        placeholders.put("attacker_uuid", session.logoutOpponent().uniqueId().toString());
        placeholders.put("attacker_type", session.logoutOpponent().entityType().name());
        placeholders.put("world", location.getWorld() == null ? "unknown" : location.getWorld().getName());
        placeholders.put("x", Integer.toString(location.getBlockX()));
        placeholders.put("y", Integer.toString(location.getBlockY()));
        placeholders.put("z", Integer.toString(location.getBlockZ()));
        placeholders.put("source_available", Boolean.toString(attacker != null));
        return Map.copyOf(placeholders);
    }

    private static String replace(String value, Map<String, String> placeholders) {
        String result = value;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return result;
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
            Instant taggedAt,
            Instant expiresAt,
            long expiresAtNanos
    ) {
    }

    public record StoredSession(
            CombatOpponent opponent,
            UUID damageSourceId,
            CombatTagReason reason,
            CombatOpponent logoutOpponent,
            UUID logoutDamageSourceId,
            long remainingNanos
    ) {
        public StoredSession {
            java.util.Objects.requireNonNull(opponent, "opponent");
            java.util.Objects.requireNonNull(damageSourceId, "damageSourceId");
            java.util.Objects.requireNonNull(reason, "reason");
            java.util.Objects.requireNonNull(logoutOpponent, "logoutOpponent");
            java.util.Objects.requireNonNull(logoutDamageSourceId, "logoutDamageSourceId");
            if (remainingNanos < 0L) {
                remainingNanos = 0L;
            }
        }
    }
}
