package nl.hauntedmc.serverfeatures.features.economy.model;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

import java.math.BigDecimal;
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
        ADMIN_ADD,
        ADMIN_REMOVE,
        ADMIN_SET,
        LOTTERY_PURCHASE,
        LOTTERY_DONATION,
        LOTTERY_PAYOUT,
        LOTTERY_REFUND,
        VAULT_DEPOSIT,
        VAULT_WITHDRAW,
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
            long orphanSettingsCount,
            long orphanEntryCount,
            long identityMismatchCount,
            long accountWithoutEntriesCount,
            long transactionWithoutEntriesCount
    ) {
        public boolean healthy() {
            return invalidBalanceCount == 0L
                    && invalidEntryCount == 0L
                    && orphanSettingsCount == 0L
                    && orphanEntryCount == 0L
                    && identityMismatchCount == 0L
                    && accountWithoutEntriesCount == 0L
                    && transactionWithoutEntriesCount == 0L;
        }
    }
}
