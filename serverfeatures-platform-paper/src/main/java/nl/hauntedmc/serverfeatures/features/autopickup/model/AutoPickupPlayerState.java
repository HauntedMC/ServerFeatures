package nl.hauntedmc.serverfeatures.features.autopickup.model;

import java.util.Objects;

public final class AutoPickupPlayerState {

    private LoadState loadState = LoadState.LOADING;
    private boolean enabled;
    private boolean persisted = true;
    private long playerId;
    private long writeRevision;
    private long generation;
    private long lastFullNoticeNanos = Long.MIN_VALUE;
    private CommandIntent pendingCommand;

    public LoadState loadState() {
        return loadState;
    }

    public void loadState(LoadState loadState) {
        this.loadState = loadState;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean persisted() {
        return persisted;
    }

    public void persisted(boolean persisted) {
        this.persisted = persisted;
    }

    public long playerId() {
        return playerId;
    }

    public void playerId(long playerId) {
        this.playerId = playerId;
    }

    public long writeRevision() {
        return writeRevision;
    }

    public void writeRevision(long writeRevision) {
        if (writeRevision < 0L) {
            throw new IllegalArgumentException("writeRevision cannot be negative");
        }
        this.writeRevision = writeRevision;
    }

    public long generation() {
        return generation;
    }

    public long nextGeneration() {
        return ++generation;
    }

    public long lastFullNoticeNanos() {
        return lastFullNoticeNanos;
    }

    public void lastFullNoticeNanos(long lastFullNoticeNanos) {
        this.lastFullNoticeNanos = lastFullNoticeNanos;
    }

    public CommandIntent pendingCommand() {
        return pendingCommand;
    }

    public void pendingCommand(CommandIntent pendingCommand) {
        this.pendingCommand = pendingCommand;
    }

    /**
     * Composes commands entered while the persisted preference is still loading.
     * Explicit states replace older intent; relative toggles preserve their actual parity.
     */
    public void queuePendingCommand(CommandIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent == CommandIntent.STATUS) {
            throw new IllegalArgumentException("STATUS cannot be queued as a preference mutation");
        }
        if (intent == CommandIntent.ENABLE || intent == CommandIntent.DISABLE) {
            pendingCommand = intent;
            return;
        }
        if (pendingCommand == null) {
            pendingCommand = CommandIntent.TOGGLE;
            return;
        }
        pendingCommand = switch (pendingCommand) {
            case ENABLE -> CommandIntent.DISABLE;
            case DISABLE -> CommandIntent.ENABLE;
            case TOGGLE -> null;
            case STATUS -> throw new IllegalStateException("STATUS cannot be a pending preference mutation");
        };
    }

    public enum LoadState {
        LOADING,
        READY,
        FAILED
    }

    public enum CommandIntent {
        TOGGLE,
        ENABLE,
        DISABLE,
        STATUS
    }
}
