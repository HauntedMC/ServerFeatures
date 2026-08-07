package nl.hauntedmc.serverfeatures.features.economy.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

/** Post-commit transfer hint. Every displayed detail is reloaded from the authoritative MySQL journal. */
public final class EconomyTransferMessage extends AbstractEventMessage {
    public static final String TYPE = "economy_transfer_completed";
    public static final int SCHEMA_VERSION = 2;

    private int schemaVersion;
    private String publisherServer;
    private String operationId;
    private long recipientPlayerId;
    private String recipientPlayerUuid;
    private String currencyId;
    private String scopeKey;
    private long publishedAt;

    @SuppressWarnings("unused")
    private EconomyTransferMessage() {
        super(TYPE);
    }

    public EconomyTransferMessage(
            String publisherServer,
            String operationId,
            long recipientPlayerId,
            String recipientPlayerUuid,
            String currencyId,
            String scopeKey,
            long publishedAt
    ) {
        super(TYPE);
        this.schemaVersion = SCHEMA_VERSION;
        this.publisherServer = publisherServer;
        this.operationId = operationId;
        this.recipientPlayerId = recipientPlayerId;
        this.recipientPlayerUuid = recipientPlayerUuid;
        this.currencyId = currencyId;
        this.scopeKey = scopeKey;
        this.publishedAt = publishedAt;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getPublisherServer() { return publisherServer; }
    public String getOperationId() { return operationId; }
    public long getRecipientPlayerId() { return recipientPlayerId; }
    public String getRecipientPlayerUuid() { return recipientPlayerUuid; }
    public String getCurrencyId() { return currencyId; }
    public String getScopeKey() { return scopeKey; }
    public long getPublishedAt() { return publishedAt; }
}
