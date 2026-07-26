package nl.hauntedmc.serverfeatures.features.chatlog.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "player_reported_chat_messages")
public class ReportedChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server", length = 100, nullable = false)
    private String server;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "message", length = 400, nullable = false)
    private String message;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "report_id", nullable = false)
    private String reportId;

    public ReportedChatMessageEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }
}
