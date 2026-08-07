package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Network-level guard preventing one currency ID from resolving to incompatible scope families. */
@Entity
@Table(
        name = "economy_currency_family",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_economy_currency_family",
                columnNames = {"network_key", "currency_id"}
        )
)
public class EconomyCurrencyFamilyEntity {
    @Id
    @Column(name = "id", length = 160, nullable = false)
    private String id;
    @Column(name = "network_key", length = 64, nullable = false)
    private String networkKey;
    @Column(name = "currency_id", length = 64, nullable = false)
    private String currencyId;
    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;
    @Column(name = "fractional_digits", nullable = false)
    private int fractionalDigits;
    @Column(name = "global_scope_key", length = 128)
    private String globalScopeKey;
    @Column(name = "family_hash", length = 64, nullable = false)
    private String familyHash;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNetworkKey() { return networkKey; }
    public void setNetworkKey(String networkKey) { this.networkKey = networkKey; }
    public String getCurrencyId() { return currencyId; }
    public void setCurrencyId(String currencyId) { this.currencyId = currencyId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public int getFractionalDigits() { return fractionalDigits; }
    public void setFractionalDigits(int fractionalDigits) { this.fractionalDigits = fractionalDigits; }
    public String getGlobalScopeKey() { return globalScopeKey; }
    public void setGlobalScopeKey(String globalScopeKey) { this.globalScopeKey = globalScopeKey; }
    public String getFamilyHash() { return familyHash; }
    public void setFamilyHash(String familyHash) { this.familyHash = familyHash; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
