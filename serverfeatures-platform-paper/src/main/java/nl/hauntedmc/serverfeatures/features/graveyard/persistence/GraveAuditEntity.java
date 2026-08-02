package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "graveyard_audit",
        indexes = {
                @Index(name = "idx_grave_audit_grave", columnList = "grave_id, created_at"),
                @Index(name = "idx_grave_audit_actor", columnList = "actor_uuid, created_at")
        }
)
public class GraveAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grave_id", length = 36, nullable = false)
    private String graveId;

    @Column(name = "operation_token", length = 36)
    private String operationToken;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "actor_uuid", length = 36)
    private String actorUuid;

    @Column(name = "old_state", length = 32)
    private String oldState;

    @Column(name = "new_state", length = 32)
    private String newState;

    @Column(name = "item_count_before", nullable = false)
    private int itemCountBefore;

    @Column(name = "item_count_after", nullable = false)
    private int itemCountAfter;

    @Column(name = "xp_before", nullable = false)
    private int experienceBefore;

    @Column(name = "xp_after", nullable = false)
    private int experienceAfter;

    @Column(name = "server_id", length = 100, nullable = false)
    private String serverId;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public Long getId() { return id; }
    public String getGraveId() { return graveId; }
    public void setGraveId(String graveId) { this.graveId = graveId; }
    public String getOperationToken() { return operationToken; }
    public void setOperationToken(String operationToken) { this.operationToken = operationToken; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActorUuid() { return actorUuid; }
    public void setActorUuid(String actorUuid) { this.actorUuid = actorUuid; }
    public String getOldState() { return oldState; }
    public void setOldState(String oldState) { this.oldState = oldState; }
    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }
    public int getItemCountBefore() { return itemCountBefore; }
    public void setItemCountBefore(int itemCountBefore) { this.itemCountBefore = itemCountBefore; }
    public int getItemCountAfter() { return itemCountAfter; }
    public void setItemCountAfter(int itemCountAfter) { this.itemCountAfter = itemCountAfter; }
    public int getExperienceBefore() { return experienceBefore; }
    public void setExperienceBefore(int experienceBefore) { this.experienceBefore = experienceBefore; }
    public int getExperienceAfter() { return experienceAfter; }
    public void setExperienceAfter(int experienceAfter) { this.experienceAfter = experienceAfter; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
