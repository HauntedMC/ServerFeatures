package nl.hauntedmc.serverfeatures.features.economy.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

/** Versioned invalidation hint. MySQL remains authoritative for every balance and setting. */
public final class EconomyBalanceMessage extends AbstractEventMessage {
    public static final String TYPE = "economy_account_invalidated";
    public static final int SCHEMA_VERSION = 2;

    private int schemaVersion;
    private String publisherServer;
    private String operationId;
    private long playerId;
    private String playerUuid;
    private String currencyId;
    private String scopeKey;
    private long balanceVersion;
    private long settingsVersion;
    private long publishedAt;

    @SuppressWarnings("unused")
    private EconomyBalanceMessage() {
        super(TYPE);
    }

    public EconomyBalanceMessage(
            String publisherServer,
            String operationId,
            long playerId,
            String playerUuid,
            String currencyId,
            String scopeKey,
            long balanceVersion,
            long settingsVersion,
            long publishedAt
    ) {
        super(TYPE);
        this.schemaVersion = SCHEMA_VERSION;
        this.publisherServer = publisherServer;
        this.operationId = operationId;
        this.playerId = playerId;
        this.playerUuid = playerUuid;
        this.currencyId = currencyId;
        this.scopeKey = scopeKey;
        this.balanceVersion = balanceVersion;
        this.settingsVersion = settingsVersion;
        this.publishedAt = publishedAt;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getPublisherServer() { return publisherServer; }
    public String getOperationId() { return operationId; }
    public long getPlayerId() { return playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public String getCurrencyId() { return currencyId; }
    public String getScopeKey() { return scopeKey; }
    public long getBalanceVersion() { return balanceVersion; }
    public long getSettingsVersion() { return settingsVersion; }
    public long getPublishedAt() { return publishedAt; }
}
