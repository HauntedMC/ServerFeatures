package nl.hauntedmc.serverfeatures.features.restart.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartLifecyclePublisher;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartMarker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RestartService {

    public enum Phase {
        IDLE,
        SCHEDULED,
        COUNTDOWN,
        FINAL_DELAY,
        PREPARING,
        DRAINING,
        SHUTTING_DOWN
    }

    public enum ScheduleResult {
        SUCCESS,
        ALREADY_ACTIVE,
        NOT_IN_FUTURE
    }

    public enum CancelResult {
        NONE,
        SCHEDULED,
        COUNTDOWN,
        FINAL_DELAY,
        PREPARING,
        TOO_LATE
    }

    private static final long TICK_MS = 50L;
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm z");

    private final Restart feature;
    private final RestartLifecyclePublisher lifecyclePublisher;
    private final AtomicLong sequenceToken = new AtomicLong(0L);
    private final AtomicBoolean shutdownCommitted = new AtomicBoolean(false);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private final Title.Times titleTimes;
    private final int waitAfterNowSeconds;
    private final long preparePublishTimeoutMillis;
    private final long prepareSettleMillis;
    private final List<Integer> scheduleDesc;
    private final Set<Integer> announcementSeconds;
    private final boolean useChat;
    private final boolean useTitles;
    private final ZoneId scheduleZone;
    private final long scheduleCheckIntervalMillis;
    private final int scheduleAnnounceHoursBefore;
    private final long drainPlayerIntervalMillis;
    private final long drainPollIntervalMillis;
    private final long drainEmptyGraceMillis;
    private final long drainMaxWaitMillis;

    private volatile Phase phase = Phase.IDLE;
    private volatile ZonedDateTime scheduledAt;
    private volatile ZonedDateTime lastAnnouncedHourStart;
    private volatile int remainingSeconds;
    private volatile boolean joinsClosed;
    private volatile RestartMarker preparedMarker;

    private List<UUID> drainQueue = List.of();
    private int drainCursor;
    private long drainDeadlineNanos;

    public RestartService(Restart feature) {
        this(feature, null);
    }

    public RestartService(Restart feature, RestartLifecyclePublisher lifecyclePublisher) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.lifecyclePublisher = lifecyclePublisher;

        int fadeInTicks = Math.max(0, feature.getInt("title_fade_in", 20));
        int stayTicks = Math.max(0, feature.getInt("title_stay", 100));
        int fadeOutTicks = Math.max(0, feature.getInt("title_fade_out", 20));
        this.titleTimes = Title.Times.times(
                Duration.ofMillis(fadeInTicks * TICK_MS),
                Duration.ofMillis(stayTicks * TICK_MS),
                Duration.ofMillis(fadeOutTicks * TICK_MS)
        );

        this.waitAfterNowSeconds = Math.max(0, feature.getInt("auto.wait_after_now_seconds", 5));
        this.preparePublishTimeoutMillis = feature.getPositiveLong(
                "autoreconnect.prepare_publish_timeout_millis",
                3_000L
        );
        this.prepareSettleMillis = Math.max(0L, feature.getLong(
                "autoreconnect.prepare_settle_millis",
                500L
        ));
        this.scheduleDesc = parseSchedule();
        this.announcementSeconds = Set.copyOf(scheduleDesc);
        this.useChat = feature.getBoolean("broadcast.use_chat", true);
        this.useTitles = feature.getBoolean("broadcast.use_titles", true);
        this.scheduleZone = readZone(feature.getString("schedule.time_zone", "system"));
        this.scheduleCheckIntervalMillis = TimeUnit.SECONDS.toMillis(
                feature.getPositiveInt("schedule.check_interval_seconds", 5)
        );
        this.scheduleAnnounceHoursBefore = Math.max(
                0,
                feature.getInt("schedule.announce_hours_before", 5)
        );
        this.drainPlayerIntervalMillis = feature.getPositiveLong(
                "drain.player_interval_millis",
                150L
        );
        this.drainPollIntervalMillis = feature.getPositiveLong(
                "drain.poll_interval_millis",
                100L
        );
        this.drainEmptyGraceMillis = Math.max(
                0L,
                feature.getLong("drain.empty_grace_millis", 300L)
        );
        this.drainMaxWaitMillis = TimeUnit.SECONDS.toMillis(
                feature.getPositiveInt("drain.max_wait_seconds", 20)
        );
    }

    public boolean startCommanded(CommandSender initiator) {
        long token;
        synchronized (this) {
            if (phase != Phase.IDLE) {
                return false;
            }
            token = beginCountdownLocked();
        }

        feature.getLogger().info(
                "Restart initiated by " + (initiator == null ? "system" : initiator.getName())
        );
        countdownTick(token);
        return true;
    }

    public boolean startAutomatic() {
        long token;
        synchronized (this) {
            if (phase != Phase.IDLE) {
                feature.getLogger().warning(
                        "Automatic restart skipped; another restart operation is active."
                );
                return false;
            }
            token = beginCountdownLocked();
        }

        feature.getLogger().info("Automatic daily restart starting.");
        countdownTick(token);
        return true;
    }

    public void forceImmediate(CommandSender initiator) {
        RestartMarker markerToCancel;
        long token;
        synchronized (this) {
            if (phase == Phase.SHUTTING_DOWN) {
                return;
            }
            markerToCancel = preparedMarker;
            invalidateAndResetLocked();
            token = sequenceToken.incrementAndGet();
            phase = Phase.FINAL_DELAY;
        }

        publishCancelSafely(markerToCancel);
        feature.getLogger().warning(
                "Forced restart initiated by " + (initiator == null ? "system" : initiator.getName())
        );
        beginPrepare(token);
    }

    public ScheduleResult scheduleRestart(ZonedDateTime target) {
        Objects.requireNonNull(target, "target");

        long token;
        ZonedDateTime normalized = target.withZoneSameInstant(scheduleZone);
        synchronized (this) {
            if (phase != Phase.IDLE) {
                return ScheduleResult.ALREADY_ACTIVE;
            }
            if (!normalized.isAfter(ZonedDateTime.now(scheduleZone))) {
                return ScheduleResult.NOT_IN_FUTURE;
            }

            token = sequenceToken.incrementAndGet();
            phase = Phase.SCHEDULED;
            scheduledAt = normalized;
            lastAnnouncedHourStart = null;
        }

        scheduleMonitorTick(token);
        return ScheduleResult.SUCCESS;
    }

    public CancelResult cancelRestart() {
        CancelResult result;
        RestartMarker markerToCancel;

        synchronized (this) {
            result = switch (phase) {
                case IDLE -> CancelResult.NONE;
                case SCHEDULED -> CancelResult.SCHEDULED;
                case COUNTDOWN -> CancelResult.COUNTDOWN;
                case FINAL_DELAY -> CancelResult.FINAL_DELAY;
                case PREPARING -> CancelResult.PREPARING;
                case DRAINING, SHUTTING_DOWN -> CancelResult.TOO_LATE;
            };

            if (result == CancelResult.NONE || result == CancelResult.TOO_LATE) {
                return result;
            }

            markerToCancel = preparedMarker;
            invalidateAndResetLocked();
        }

        publishCancelSafely(markerToCancel);
        return result;
    }

    public void shutdown() {
        RestartMarker markerToCancel;
        synchronized (this) {
            if (phase == Phase.SHUTTING_DOWN || shutdownStarted.get()) {
                return;
            }
            markerToCancel = preparedMarker;
            invalidateAndResetLocked();
        }
        publishCancelSafely(markerToCancel);
    }

    public void cancelIfRunning() {
        shutdown();
    }

    public Phase getPhase() {
        return phase;
    }

    public ZonedDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getRemainingSeconds() {
        return Math.max(0, remainingSeconds);
    }

    public int getPlayersRemaining() {
        if (phase != Phase.PREPARING && phase != Phase.DRAINING) {
            return 0;
        }
        return feature.getPlugin().getServer().getOnlinePlayers().size();
    }

    public boolean isAcceptingJoins() {
        return !joinsClosed;
    }

    public ZoneId getScheduleZone() {
        return scheduleZone;
    }

    public String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) return "";
        return DISPLAY_FORMAT.format(dateTime.withZoneSameInstant(scheduleZone));
    }

    private long beginCountdownLocked() {
        long token = sequenceToken.incrementAndGet();
        phase = Phase.COUNTDOWN;
        scheduledAt = null;
        lastAnnouncedHourStart = null;
        remainingSeconds = scheduleDesc.getFirst();
        joinsClosed = false;
        preparedMarker = null;
        shutdownCommitted.set(false);
        shutdownStarted.set(false);
        return token;
    }

    private void countdownTick(long token) {
        int current;
        boolean announce;
        boolean finalTick;
        int finalDelay;

        synchronized (this) {
            if (!isCurrentLocked(token, Phase.COUNTDOWN)) {
                return;
            }

            current = remainingSeconds;
            announce = announcementSeconds.contains(current);
            finalTick = current == 0;
            if (finalTick) {
                finalDelay = waitAfterNowSeconds;
                phase = finalDelay > 0 ? Phase.FINAL_DELAY : Phase.PREPARING;
            } else {
                remainingSeconds = current - 1;
                finalDelay = 0;
            }
        }

        if (announce) {
            announceRemaining(current);
        }

        if (finalTick) {
            if (finalDelay > 0) {
                scheduleMillis(finalDelay * 1_000L, () -> beginPrepare(token));
            } else {
                beginPrepare(token);
            }
            return;
        }

        scheduleMillis(1_000L, () -> countdownTick(token));
    }

    private void scheduleMonitorTick(long token) {
        ZonedDateTime now;
        ZonedDateTime target;

        synchronized (this) {
            if (!isCurrentLocked(token, Phase.SCHEDULED)) {
                return;
            }
            now = ZonedDateTime.now(scheduleZone);
            target = scheduledAt;
        }

        if (target == null) {
            return;
        }
        tryHourlyAnnouncement(token, now, target);

        if (!now.isBefore(target)) {
            synchronized (this) {
                if (!isCurrentLocked(token, Phase.SCHEDULED)) {
                    return;
                }
                phase = Phase.COUNTDOWN;
                scheduledAt = null;
                lastAnnouncedHourStart = null;
                remainingSeconds = scheduleDesc.getFirst();
            }
            countdownTick(token);
            return;
        }

        scheduleMillis(scheduleCheckIntervalMillis, () -> scheduleMonitorTick(token));
    }

    private void tryHourlyAnnouncement(long token, ZonedDateTime now, ZonedDateTime target) {
        boolean announce;
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.SCHEDULED) || scheduleAnnounceHoursBefore <= 0) {
                return;
            }

            ZonedDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);
            if (lastAnnouncedHourStart != null && !hourStart.isAfter(lastAnnouncedHourStart)) {
                return;
            }
            lastAnnouncedHourStart = hourStart;

            ZonedDateTime windowStart = target.minusHours(scheduleAnnounceHoursBefore);
            announce = !hourStart.isBefore(windowStart) && hourStart.isBefore(target);
        }

        if (announce && useChat) {
            String datetime = formatDateTime(target);
            for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
                Component chat = feature.getLocalizationHandler()
                        .getMessage("restart.schedule.announce_chat")
                        .with("datetime", datetime)
                        .forAudience(player)
                        .build();
                player.sendMessage(chat);
            }
        }
    }

    private void beginPrepare(long token) {
        List<Player> players;
        synchronized (this) {
            if (sequenceToken.get() != token
                    || (phase != Phase.FINAL_DELAY && phase != Phase.PREPARING)) {
                return;
            }
            phase = Phase.PREPARING;
            joinsClosed = true;
            shutdownCommitted.set(true);
            players = List.copyOf(feature.getPlugin().getServer().getOnlinePlayers());
        }

        if (lifecyclePublisher == null) {
            beginDrain(token);
            return;
        }

        RestartLifecyclePublisher.Preparation preparation = lifecyclePublisher.prepare(players);
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.PREPARING)) {
                publishCancelSafely(preparation.marker());
                return;
            }
            preparedMarker = preparation.marker();
        }

        preparation.publication()
                .orTimeout(preparePublishTimeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete((published, throwable) -> {
                    synchronized (RestartService.this) {
                        if (!isCurrentLocked(token, Phase.PREPARING)
                                || !shutdownCommitted.get()) {
                            return;
                        }
                    }

                    long settleMillis = 0L;
                    if (throwable != null) {
                        feature.getLogger().warning(
                                "Restart PREPARE could not be confirmed; continuing without "
                                        + "guaranteed autoreconnect: " + rootMessage(throwable)
                        );
                    } else {
                        settleMillis = prepareSettleMillis;
                    }
                    scheduleMillis(settleMillis, () -> beginDrain(token));
                });
    }

    private void beginDrain(long token) {
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.PREPARING) || !shutdownCommitted.get()) {
                return;
            }
            phase = Phase.DRAINING;
            drainQueue = onlinePlayerIds();
            drainCursor = 0;
            drainDeadlineNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(drainMaxWaitMillis);
        }

        dispatchSaveAll("before player drain");
        drainTick(token);
    }

    private void drainTick(long token) {
        Player next = nextQueuedPlayer(token);
        if (next != null) {
            kickForRestart(next);
            scheduleMillis(drainPlayerIntervalMillis, () -> drainTick(token));
            return;
        }

        scheduleMillis(drainEmptyGraceMillis, () -> verifyDrain(token));
    }

    private Player nextQueuedPlayer(long token) {
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.DRAINING)) {
                return null;
            }

            while (drainCursor < drainQueue.size()) {
                UUID playerId = drainQueue.get(drainCursor++);
                Player player = feature.getPlugin().getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    return player;
                }
            }
            return null;
        }
    }

    private void verifyDrain(long token) {
        List<UUID> remaining;
        boolean timedOut;
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.DRAINING)) {
                return;
            }
            remaining = onlinePlayerIds();
            timedOut = !remaining.isEmpty() && System.nanoTime() >= drainDeadlineNanos;
            if (!remaining.isEmpty()) {
                drainQueue = remaining;
                drainCursor = 0;
            }
        }

        if (remaining.isEmpty()) {
            shutdownServer(token);
            return;
        }

        if (!timedOut) {
            scheduleMillis(drainPollIntervalMillis, () -> drainTick(token));
            return;
        }

        feature.getLogger().warning(
                "Restart player drain timed out with " + remaining.size()
                        + " player(s) still connected; applying a final staggered kick pass."
        );
        finalDrainTick(token);
    }

    private void finalDrainTick(long token) {
        Player next = nextQueuedPlayer(token);
        if (next != null) {
            kickForRestart(next);
            scheduleMillis(drainPlayerIntervalMillis, () -> finalDrainTick(token));
            return;
        }
        scheduleMillis(Math.max(500L, drainEmptyGraceMillis), () -> shutdownServer(token));
    }

    private void shutdownServer(long token) {
        int remaining;
        synchronized (this) {
            if (!isCurrentLocked(token, Phase.DRAINING)
                    || !shutdownCommitted.get()
                    || !shutdownStarted.compareAndSet(false, true)) {
                return;
            }
            phase = Phase.SHUTTING_DOWN;
            joinsClosed = true;
            remaining = feature.getPlugin().getServer().getOnlinePlayers().size();
        }

        if (remaining > 0) {
            feature.getLogger().warning(
                    "Proceeding with shutdown after the bounded drain fail-safe; " + remaining
                            + " player(s) are still reported online."
            );
        }
        dispatchSaveAll("after player drain");
        feature.getLogger().info("Player drain complete; initiating Paper shutdown.");
        feature.getPlugin().getServer().shutdown();
    }

    private void kickForRestart(Player player) {
        Component kick = feature.getLocalizationHandler()
                .getMessage("restart.kick")
                .forAudience(player)
                .build();
        try {
            player.kick(kick);
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Failed to kick " + player.getName() + " during restart drain: "
                            + rootMessage(exception)
            );
        }
    }

    private void announceRemaining(int seconds) {
        TimeFmt time = TimeFmt.of(seconds);
        for (Player player : feature.getPlugin().getServer().getOnlinePlayers()) {
            Component title;
            Component subtitle;
            Component chat;

            if (seconds == 0) {
                title = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.title")
                        .forAudience(player)
                        .build();
                subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.subtitle")
                        .forAudience(player)
                        .build();
                chat = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.chat")
                        .forAudience(player)
                        .build();
            } else {
                title = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.title")
                        .with("readable", time.readable())
                        .forAudience(player)
                        .build();
                subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.subtitle")
                        .with("readable", time.readable())
                        .forAudience(player)
                        .build();
                chat = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.chat")
                        .with("readable", time.readable())
                        .forAudience(player)
                        .build();
            }

            if (useTitles) {
                player.showTitle(Title.title(title, subtitle, titleTimes));
            }
            if (useChat) {
                player.sendMessage(chat);
            }
        }
    }

    private boolean isCurrentLocked(long token, Phase expected) {
        return sequenceToken.get() == token && phase == expected;
    }

    private void invalidateAndResetLocked() {
        sequenceToken.incrementAndGet();
        phase = Phase.IDLE;
        scheduledAt = null;
        lastAnnouncedHourStart = null;
        remainingSeconds = 0;
        joinsClosed = false;
        preparedMarker = null;
        drainQueue = List.of();
        drainCursor = 0;
        drainDeadlineNanos = 0L;
        shutdownCommitted.set(false);
        shutdownStarted.set(false);
    }

    private List<UUID> onlinePlayerIds() {
        return feature.getPlugin().getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
    }

    private void dispatchSaveAll(String phaseDescription) {
        try {
            feature.getPlugin().getServer().dispatchCommand(
                    feature.getPlugin().getServer().getConsoleSender(),
                    "save-all flush"
            );
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Failed to dispatch 'save-all flush' " + phaseDescription + ": "
                            + rootMessage(exception)
            );
        }
    }

    private void publishCancelSafely(RestartMarker marker) {
        if (lifecyclePublisher == null || marker == null) {
            return;
        }
        try {
            lifecyclePublisher.publishCancel(marker).exceptionally(throwable -> {
                feature.getLogger().warning(
                        "Failed to publish restart CANCEL: " + rootMessage(throwable)
                );
                return null;
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Failed to schedule restart CANCEL publication: " + rootMessage(exception)
            );
        }
    }

    private void scheduleMillis(long delayMillis, Runnable action) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                action,
                BukkitTime.ticks(millisToTicksCeil(delayMillis))
        );
    }

    private List<Integer> parseSchedule() {
        Object raw = feature.getConfigHandler().get("announce.schedule");
        List<Integer> parsed = new ArrayList<>();
        List<?> values = raw instanceof List<?> list ? list : List.of(60, 30, 0);

        for (Object value : values) {
            int seconds = parseNonNegativeInt(value);
            if (seconds >= 0) {
                parsed.add(seconds);
            }
        }

        parsed.sort(Comparator.reverseOrder());
        List<Integer> unique = new ArrayList<>();
        Integer previous = null;
        for (Integer value : parsed) {
            if (!Objects.equals(previous, value)) {
                unique.add(value);
            }
            previous = value;
        }
        if (unique.isEmpty() || unique.getLast() != 0) {
            unique.add(0);
        }
        return List.copyOf(unique);
    }

    private ZoneId readZone(String configured) {
        if (configured == null
                || configured.isBlank()
                || "system".equalsIgnoreCase(configured.trim())) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (DateTimeException exception) {
            feature.getLogger().warning(
                    "Invalid schedule.time_zone '" + configured + "'; using the system timezone."
            );
            return ZoneId.systemDefault();
        }
    }

    private static int parseNonNegativeInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(-1, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(-1, Integer.parseInt(String.valueOf(value).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static long millisToTicksCeil(long millis) {
        if (millis <= 0L) {
            return 0L;
        }
        return Math.max(1L, (millis + TICK_MS - 1L) / TICK_MS);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private record TimeFmt(String readable) {
        static TimeFmt of(int totalSeconds) {
            int minutes = Math.max(0, totalSeconds) / 60;
            int seconds = Math.max(0, totalSeconds) % 60;
            String readable = minutes > 0 && seconds > 0
                    ? minutes + "m " + seconds + "s"
                    : minutes > 0 ? minutes + "m" : seconds + "s";
            return new TimeFmt(readable);
        }
    }
}
