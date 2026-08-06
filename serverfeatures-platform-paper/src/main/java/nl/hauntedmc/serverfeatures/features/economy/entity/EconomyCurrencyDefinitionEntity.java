package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "system_economy_currency_definition",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_economy_currency_definition",
                columnNames = {"currency_id", "scope_key"}
        )
)
public class EconomyCurrencyDefinitionEntity {
    @Id
    @Column(name = "id", length = 192, nullable = false)
    private String id;
    @Column(name = "currency_id", length = 64, nullable = false)
    private String currencyId;
    @Column(name = "scope_key", length = 128, nullable = false)
    private String scopeKey;
    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;
    @Column(name = "fractional_digits", nullable = false)
    private int fractionalDigits;
    @Column(name = "starting_balance", precision = 38, scale = 8, nullable = false)
    private BigDecimal startingBalance;
    @Column(name = "minimum_balance", precision = 38, scale = 8, nullable = false)
    private BigDecimal minimumBalance;
    @Column(name = "maximum_balance", precision = 38, scale = 8, nullable = false)
    private BigDecimal maximumBalance;
    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;
    @Column(name = "definition_hash", length = 64, nullable = false)
    private String definitionHash;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCurrencyId() { return currencyId; }
    public void setCurrencyId(String currencyId) { this.currencyId = currencyId; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public int getFractionalDigits() { return fractionalDigits; }
    public void setFractionalDigits(int fractionalDigits) { this.fractionalDigits = fractionalDigits; }
    public BigDecimal getStartingBalance() { return startingBalance; }
    public void setStartingBalance(BigDecimal startingBalance) { this.startingBalance = startingBalance; }
    public BigDecimal getMinimumBalance() { return minimumBalance; }
    public void setMinimumBalance(BigDecimal minimumBalance) { this.minimumBalance = minimumBalance; }
    public BigDecimal getMaximumBalance() { return maximumBalance; }
    public void setMaximumBalance(BigDecimal maximumBalance) { this.maximumBalance = maximumBalance; }
    public boolean isAllowNegative() { return allowNegative; }
    public void setAllowNegative(boolean allowNegative) { this.allowNegative = allowNegative; }
    public String getDefinitionHash() { return definitionHash; }
    public void setDefinitionHash(String definitionHash) { this.definitionHash = definitionHash; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
