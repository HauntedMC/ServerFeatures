package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "graveyard_leases")
public class GraveLeaseEntity {
    @Id
    @Column(name = "scope_key", length = 220, nullable = false)
    private String scopeKey;

    @Column(name = "owner_token", length = 36, nullable = false)
    private String ownerToken;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getOwnerToken() { return ownerToken; }
    public void setOwnerToken(String ownerToken) { this.ownerToken = ownerToken; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
