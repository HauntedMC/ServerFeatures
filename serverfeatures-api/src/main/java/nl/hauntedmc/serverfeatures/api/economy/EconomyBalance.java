package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Objects;

/** Committed balance snapshot. */
public record EconomyBalance(EconomyAccountRef account, BigDecimal balance, long version) {
    public EconomyBalance {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(balance, "balance");
    }
}
