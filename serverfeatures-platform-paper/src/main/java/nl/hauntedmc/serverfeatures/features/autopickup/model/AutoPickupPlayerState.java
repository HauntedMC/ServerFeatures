package nl.hauntedmc.serverfeatures.features.autopickup.model;

public final class AutoPickupPlayerState {

    private LoadState loadState = LoadState.LOADING;
    private boolean enabled;
    private boolean persisted = true;
    private long playerId;
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
