package nl.hauntedmc.serverfeatures.features.economy.model;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

    public record HistoryPage(List<HistoryItem> entries, int page, boolean hasMore) {
        public HistoryPage {
            entries = List.copyOf(entries);
        }

        @Override
        public List<HistoryItem> entries() {
            return List.copyOf(new ArrayList<>(entries));
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
