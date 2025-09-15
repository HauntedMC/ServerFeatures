package nl.hauntedmc.serverfeatures.features.afk.internal.engine.player;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AfkPlayerState {
    private boolean afk;
    private boolean suspicious;
    private long lastActivity;
    private long afkSince;

    private long lastMove;
    private long lastAux;

    private long afkLockUntil;

    private final Deque<Long> antiTimes = new ArrayDeque<>();

    public boolean isAfk() { return afk; }
    public void setAfk(boolean afk) { this.afk = afk; }

    public boolean isSuspicious() { return suspicious; }
    public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }

    public long lastActivity() { return lastActivity; }
    public void touchActivity(long ts) { this.lastActivity = ts; }

    public long afkSince() { return afkSince; }
    public void setAfkSince(long ts) { this.afkSince = ts; }

    public long lastMove() { return lastMove; }
    public void setLastMove(long ts) { this.lastMove = ts; }

    public long lastAux() { return lastAux; }
    public void setLastAux(long ts) { this.lastAux = ts; }

    public Deque<Long> antiTimes() { return antiTimes; }
    public void clearAntiTimes() { antiTimes.clear(); }

    public long afkLockUntil() { return afkLockUntil; }
    public void setAfkLockUntil(long ts) { this.afkLockUntil = ts; }
    public boolean isAfkLocked(long now) { return afkLockUntil > now; }

    public void resetComboSignals() {
        this.lastMove = 0L;
        this.lastAux = 0L;
    }
}
