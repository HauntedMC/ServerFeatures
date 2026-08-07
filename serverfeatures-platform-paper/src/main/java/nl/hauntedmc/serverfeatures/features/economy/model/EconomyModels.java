package nl.hauntedmc.serverfeatures.features.economy.model;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EconomyModels {
    private EconomyModels() {
    }

    public record Identity(long playerId, UUID playerUuid, String playerName) {
        public Identity {
            if (playerId <= 0L) {
                throw new IllegalArgumentException("playerId must be positive");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid must not be null");
            }
            playerName = playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName.trim();
        }
    }

    public record Account(
            String accountId,
            Identity identity,
            String currencyId,
            String scopeKey,
            BigDecimal balance,
            long version,
            long settingsVersion,
            boolean paymentsEnabled,
            AccountStatus status
    ) {
    }

    public enum AccountStatus {
        ACTIVE,
        FROZEN
    }

    public enum TransactionType {
        ACCOUNT_CREATED,
        DEPOSIT,
        WITHDRAW,
        SET,
        TRANSFER,
        PAYMENTS_ENABLED,
        PAYMENTS_DISABLED,
        ACCOUNT_FROZEN,
        ACCOUNT_UNFROZEN
    }

    public record MutationOutcome(
            EconomyResultStatus status,
            UUID operationId,
            BigDecimal balance,
            BigDecimal counterpartBalance,
            String message,
            Account account,
            Account counterpart
    ) {
        public boolean successful() {
            return status == EconomyResultStatus.SUCCESS || status == EconomyResultStatus.IDEMPOTENT_REPLAY;
        }
    }

    public record HistoryItem(
            long transactionId,
            UUID operationId,
            String transactionType,
            BigDecimal delta,
            BigDecimal balanceAfter,
            String actorName,
            String reason,
            long createdAt
    ) {
    }

    public static final class HistoryPage {
        private final List<HistoryItem> entries;
        private final int page;
        private final boolean hasMore;

        public HistoryPage(List<HistoryItem> entries, int page, boolean hasMore) {
            this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            this.page = page;
            this.hasMore = hasMore;
        }

        public List<HistoryItem> entries() {
            return List.copyOf(entries);
        }

        public int page() {
            return page;
        }

        public boolean hasMore() {
            return hasMore;
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof HistoryPage other)) {
                return false;
            }
            return page == other.page && hasMore == other.hasMore && entries.equals(other.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries, page, hasMore);
        }

        @Override
        public String toString() {
            return "HistoryPage[entries=" + entries + ", page=" + page + ", hasMore=" + hasMore + "]";
        }
    }

    public record TopEntry(long playerId, UUID playerUuid, String playerName, BigDecimal balance) {
    }

    /**
     * Version-independent monetary definition persisted for safe cross-server discovery.
     *
     * <p>Display text, commands and Vault selection are intentionally absent: those are local
     * server choices. Every remaining field affects account creation, arithmetic or payment
     * enforcement and is therefore part of the immutable definition fingerprint.</p>
     */
    public record CurrencyDefinition(
            String currencyId,
            EconomyScope scope,
            int fractionalDigits,
            BigDecimal startingBalance,
            BigDecimal minimumBalance,
            BigDecimal maximumBalance,
            boolean allowNegative,
            RoundingMode rounding,
            boolean paymentsDefaultEnabled,
            BigDecimal paymentMinimum,
            BigDecimal paymentMaximum,
            BigDecimal confirmationThreshold,
            BigDecimal dailySendLimit,
            BigDecimal dailyReceiveLimit,
            Duration paymentCooldown
    ) {
        public CurrencyDefinition {
            currencyId = Objects.requireNonNull(currencyId, "currencyId");
            scope = Objects.requireNonNull(scope, "scope");
            if (fractionalDigits < 0 || fractionalDigits > 8) {
                throw new IllegalArgumentException("fractionalDigits must be between 0 and 8");
            }
            startingBalance = Objects.requireNonNull(startingBalance, "startingBalance");
            minimumBalance = Objects.requireNonNull(minimumBalance, "minimumBalance");
            maximumBalance = Objects.requireNonNull(maximumBalance, "maximumBalance");
            rounding = Objects.requireNonNull(rounding, "rounding");
            paymentMinimum = Objects.requireNonNull(paymentMinimum, "paymentMinimum");
            paymentMaximum = Objects.requireNonNull(paymentMaximum, "paymentMaximum");
            confirmationThreshold = Objects.requireNonNull(confirmationThreshold, "confirmationThreshold");
            dailySendLimit = Objects.requireNonNull(dailySendLimit, "dailySendLimit");
            dailyReceiveLimit = Objects.requireNonNull(dailyReceiveLimit, "dailyReceiveLimit");
            paymentCooldown = Objects.requireNonNull(paymentCooldown, "paymentCooldown");
            if (currencyId.isBlank()) {
                throw new IllegalArgumentException("currencyId must not be blank");
            }
            validateStorageAmount(startingBalance, fractionalDigits, "startingBalance");
            validateStorageAmount(minimumBalance, fractionalDigits, "minimumBalance");
            validateStorageAmount(maximumBalance, fractionalDigits, "maximumBalance");
            validateStorageAmount(paymentMinimum, fractionalDigits, "paymentMinimum");
            validateStorageAmount(paymentMaximum, fractionalDigits, "paymentMaximum");
            validateStorageAmount(confirmationThreshold, fractionalDigits, "confirmationThreshold");
            validateStorageAmount(dailySendLimit, fractionalDigits, "dailySendLimit");
            validateStorageAmount(dailyReceiveLimit, fractionalDigits, "dailyReceiveLimit");
            if (minimumBalance.compareTo(maximumBalance) > 0
                    || startingBalance.compareTo(minimumBalance) < 0
                    || startingBalance.compareTo(maximumBalance) > 0) {
                throw new IllegalArgumentException("Balance bounds are inconsistent");
            }
            if (!allowNegative && minimumBalance.signum() < 0) {
                throw new IllegalArgumentException("Negative minimum requires allowNegative");
            }
            if (paymentMinimum.signum() <= 0 || paymentMaximum.signum() < 0
                    || confirmationThreshold.signum() < 0 || dailySendLimit.signum() < 0
                    || dailyReceiveLimit.signum() < 0) {
                throw new IllegalArgumentException("Payment limits must be non-negative and paymentMinimum must be positive");
            }
            if (paymentMaximum.signum() > 0 && paymentMaximum.compareTo(paymentMinimum) < 0
                    || dailySendLimit.signum() > 0 && dailySendLimit.compareTo(paymentMinimum) < 0
                    || dailyReceiveLimit.signum() > 0 && dailyReceiveLimit.compareTo(paymentMinimum) < 0) {
                throw new IllegalArgumentException("Enabled payment limits must not be below paymentMinimum");
            }
            if (paymentCooldown.isNegative() || paymentCooldown.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("paymentCooldown must be between zero and one hour");
            }
        }

        /** Matches the configured amount bounds and the database's DECIMAL(38,8) representation. */
        private static void validateStorageAmount(BigDecimal value, int fractionalDigits, String field) {
            long integerDigits = (long) value.precision() - value.scale();
            if (value.scale() != fractionalDigits || value.precision() > 38
                    || value.signum() != 0 && integerDigits > 30L) {
                throw new IllegalArgumentException(field + " is not a canonical DECIMAL(38,8) currency amount");
            }
        }
    }

    /** A shared-scope definition discovered in MySQL; legacy rows may not yet be importable. */
    public record DiscoveredCurrencyDefinition(
            String currencyId,
            EconomyScope scope,
            CurrencyDefinition definition,
            long createdAt,
            long updatedAt
    ) {
        public DiscoveredCurrencyDefinition {
            currencyId = Objects.requireNonNull(currencyId, "currencyId");
            scope = Objects.requireNonNull(scope, "scope");
        }

        public boolean importable() {
            return definition != null;
        }
    }

    public record TransferReceipt(
            UUID operationId,
            Identity sender,
            Identity recipient,
            String currencyId,
            String scopeKey,
            BigDecimal amount,
            BigDecimal recipientBalanceAfter
    ) {
    }

    public record VerificationReport(
            long accountCount,
            long transactionCount,
            long invalidBalanceCount,
            long invalidEntryCount,
            long invalidTransactionCount,
            long orphanSettingsCount,
            long orphanEntryCount,
            long identityMismatchCount,
            long entryAccountMismatchCount,
            long accountWithoutEntriesCount,
            long transactionWithoutEntriesCount,
            long balanceJournalMismatchCount,
            long journalContinuityErrorCount
    ) {
        public boolean healthy() {
            return invalidBalanceCount == 0L
                    && invalidEntryCount == 0L
                    && invalidTransactionCount == 0L
                    && orphanSettingsCount == 0L
                    && orphanEntryCount == 0L
                    && identityMismatchCount == 0L
                    && entryAccountMismatchCount == 0L
                    && accountWithoutEntriesCount == 0L
                    && transactionWithoutEntriesCount == 0L
                    && balanceJournalMismatchCount == 0L
                    && journalContinuityErrorCount == 0L;
        }
    }
}
