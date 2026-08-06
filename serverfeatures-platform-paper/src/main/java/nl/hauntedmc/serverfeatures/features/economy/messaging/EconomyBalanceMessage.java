package nl.hauntedmc.serverfeatures.features.economy.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

import java.math.BigDecimal;

/** Versioned cache-invalidation/update message for shared economy scopes. */
public final class EconomyBalanceMessage extends AbstractEventMessage {
    public static final String TYPE = "economy_balance_update";
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion;
    private String publisherServer;
    private String operationId;
    private long playerId;
    private String playerUuid;
    private String playerName;
    private String currencyId;
    private String scopeKey;
    private BigDecimal balance;
    private long version;
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
            String playerName,
            String currencyId,
            String scopeKey,
            BigDecimal balance,
            long version,
            long publishedAt
    ) {
        super(TYPE);
        this.schemaVersion = SCHEMA_VERSION;
        this.publisherServer = publisherServer;
        this.operationId = operationId;
        this.playerId = playerId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.currencyId = currencyId;
        this.scopeKey = scopeKey;
        this.balance = balance;
        this.version = version;
        this.publishedAt = publishedAt;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getPublisherServer() { return publisherServer; }
    public String getOperationId() { return operationId; }
    public long getPlayerId() { return playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getCurrencyId() { return currencyId; }
    public String getScopeKey() { return scopeKey; }
    public BigDecimal getBalance() { return balance; }
    public long getVersion() { return version; }
    public long getPublishedAt() { return publishedAt; }
}
