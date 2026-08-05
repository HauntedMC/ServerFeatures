package nl.hauntedmc.serverfeatures.features.autopickup.model;

public final class AutoPickupPlayerState {

    private boolean enabled;
    private long lastFullNoticeNanos = Long.MIN_VALUE;

    public AutoPickupPlayerState(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long lastFullNoticeNanos() {
        return lastFullNoticeNanos;
    }

    public void lastFullNoticeNanos(long lastFullNoticeNanos) {
        this.lastFullNoticeNanos = lastFullNoticeNanos;
    }

    public enum CommandIntent {
        TOGGLE,
        ENABLE,
        DISABLE,
        STATUS
    }
}
