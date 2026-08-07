package nl.hauntedmc.serverfeatures.features.sanctions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Paper-side ORM mapping for the shared ProxyFeatures {@code player_sanctions} table.
 *
 * <p>ProxyFeatures 3.3 no longer exposes runtime persistence entities through its shared artifacts.
 * ServerFeatures therefore owns this mapping and depends only on the stable database schema, rather
 * than on Velocity implementation classes.</p>
 */
@Entity
@Table(name = "player_sanctions", indexes = {
        @Index(name = "idx_active_type", columnList = "active,type"),
        @Index(name = "idx_target_player", columnList = "target_player_id"),
        @Index(name = "idx_target_ip", columnList = "target_ip"),
        @Index(name = "idx_active_expires", columnList = "active,expires_at")
})
public class SanctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private SanctionType type;

    @Column(name = "target_player_id")
    private Long targetPlayerId;

    @Column(name = "target_ip", length = 64)
    private String targetIp;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "actor_player_id")
    private Long actorPlayerId;

    @Column(name = "actor_name", length = 64)
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public SanctionType getType() {
        return type;
    }

    public void setType(SanctionType type) {
        this.type = type;
    }

    public Long getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(Long targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getActorPlayerId() {
        return actorPlayerId;
    }

    public void setActorPlayerId(Long actorPlayerId) {
        this.actorPlayerId = actorPlayerId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired(Instant now) {
        return !isPermanent() && expiresAt.isBefore(now);
    }
}
