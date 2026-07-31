package nl.hauntedmc.serverfeatures.features.commandrelay.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "command_relay_logs",
        indexes = {
                @Index(name = "idx_cmdrelay_origin_created", columnList = "origin_server, created_at"),
                @Index(name = "idx_cmdrelay_alias_created", columnList = "command_alias, created_at"),
                @Index(name = "idx_cmdrelay_event_created", columnList = "event_type, created_at")
        }
)
public class CommandRelayAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relay_channel", length = 100)
    private String relayChannel;

    @Column(name = "origin_server", length = 100)
    private String originServer;

    @Column(name = "command_alias", length = 64)
    private String commandAlias;

    @Column(name = "command_text", length = 512)
    private String commandText;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "details", length = 512)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    public Long getId() {
        return id;
    }

    public String getRelayChannel() {
        return relayChannel;
    }

    public void setRelayChannel(String relayChannel) {
        this.relayChannel = relayChannel;
    }

    public String getOriginServer() {
        return originServer;
    }

    public void setOriginServer(String originServer) {
        this.originServer = originServer;
    }

    public String getCommandAlias() {
        return commandAlias;
    }

    public void setCommandAlias(String commandAlias) {
        this.commandAlias = commandAlias;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
