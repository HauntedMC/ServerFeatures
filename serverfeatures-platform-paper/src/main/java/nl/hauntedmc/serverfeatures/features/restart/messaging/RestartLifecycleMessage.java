package nl.hauntedmc.serverfeatures.features.restart.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

import java.util.List;
import java.util.regex.Pattern;

/** Wire-compatible restart lifecycle message consumed by ProxyFeatures. */
public final class RestartLifecycleMessage extends AbstractEventMessage {

    public static final String TYPE = "server_restart_lifecycle";
    public static final String ACTION_PREPARE = "PREPARE";
    public static final String ACTION_READY = "READY";
    public static final String ACTION_CANCEL = "CANCEL";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,200}");

    // Gson populates these fields after invoking the no-arg constructor.
    private String operationId;
    private String restartId;
    private String action;
    private String serverName;
    private long createdAtEpochMillis;
    private long expiresAtEpochMillis;
    private long reconnectDelayMillis;
    private long playerIntervalMillis;
    private List<String> playerIds;

    @SuppressWarnings("unused")
    private RestartLifecycleMessage() {
        super(TYPE);
        this.playerIds = List.of();
    }

    public RestartLifecycleMessage(
            String operationId,
            String restartId,
            String action,
            String serverName,
            long createdAtEpochMillis,
            long expiresAtEpochMillis,
            long reconnectDelayMillis,
            long playerIntervalMillis,
            List<String> playerIds
    ) {
        super(TYPE);
        requireSafeId(operationId, "operationId");
        requireSafeId(restartId, "restartId");
        if (!ACTION_PREPARE.equals(action) && !ACTION_READY.equals(action) && !ACTION_CANCEL.equals(action)) {
            throw new IllegalArgumentException("Unsupported restart lifecycle action: " + action);
        }
        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("serverName must not be blank");
        }
        this.operationId = operationId;
        this.restartId = restartId;
        this.action = action;
        this.serverName = serverName.trim();
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
        this.reconnectDelayMillis = Math.max(0L, reconnectDelayMillis);
        this.playerIntervalMillis = Math.max(0L, playerIntervalMillis);
        this.playerIds = playerIds == null ? List.of() : List.copyOf(playerIds);
    }

    public String getOperationId() {
        return operationId;
    }

    public String getRestartId() {
        return restartId;
    }

    public String getAction() {
        return action;
    }

    public String getServerName() {
        return serverName;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public long getExpiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    public long getReconnectDelayMillis() {
        return reconnectDelayMillis;
    }

    public long getPlayerIntervalMillis() {
        return playerIntervalMillis;
    }

    public List<String> getPlayerIds() {
        return playerIds == null ? List.of() : List.copyOf(playerIds);
    }

    private static void requireSafeId(String value, String name) {
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + SAFE_ID.pattern());
        }
    }
}
