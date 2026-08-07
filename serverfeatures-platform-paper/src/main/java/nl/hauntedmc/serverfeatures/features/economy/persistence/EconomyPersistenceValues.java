package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.CurrencyDefinition;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Centralizes database representation, identifiers, and monetary validation. */
final class EconomyPersistenceValues {
    private static final int DATABASE_SCALE = 8;

    private EconomyPersistenceValues() {
    }

    static BigDecimal normalizeMutationAmount(TransactionType type, BigDecimal amount, EconomySettings.Currency currency) {
        if (type == TransactionType.SET) {
            BigDecimal normalized = normalize(amount, currency);
            validateBalance(normalized, currency);
            return normalized;
        }
        if (type == TransactionType.ACCOUNT_CREATED || type == TransactionType.PAYMENTS_ENABLED
                || type == TransactionType.PAYMENTS_DISABLED || type == TransactionType.ACCOUNT_FROZEN
                || type == TransactionType.ACCOUNT_UNFROZEN) {
            throw new IllegalArgumentException("Account-setting operations do not accept an amount");
        }
        return normalizePositive(amount, currency);
    }

    static BigDecimal normalizePositive(BigDecimal amount, EconomySettings.Currency currency) {
        BigDecimal normalized = normalize(amount, currency);
        if (normalized.signum() <= 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount must be positive");
        }
        return normalized;
    }

    static void validateBalance(BigDecimal balance, EconomySettings.Currency currency) {
        if (!currency.balances().allowNegative() && balance.signum() < 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INSUFFICIENT_FUNDS, "Insufficient funds");
        }
        if (balance.compareTo(currency.balances().minimum()) < 0) {
            throw new EconomyRejectedException(EconomyResultStatus.INSUFFICIENT_FUNDS, "Balance would fall below minimum");
        }
        if (balance.compareTo(currency.balances().maximum()) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Balance would exceed maximum");
        }
    }

    static BigDecimal databaseAmount(BigDecimal value) {
        BigDecimal normalized;
        try {
            normalized = value.setScale(DATABASE_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount exceeds supported decimal precision");
        }
        if (normalized.precision() > 38) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount exceeds DECIMAL(38,8) storage precision");
        }
        return normalized;
    }

    static String accountId(long playerId, String currencyId, String scopeKey) {
        return playerId + ":" + currencyId + ":" + hash(scopeKey);
    }

    static String definitionId(String currencyId, String scopeKey) {
        return currencyId + ":" + hash(scopeKey);
    }

    static String definitionHash(EconomySettings.Currency currency) {
        return definitionHash(EconomyDefinitionPayload.fromCurrency(currency));
    }

    /** Hashes precisely the monetary fields stored in a discoverable currency definition. */
    static String definitionHash(CurrencyDefinition definition) {
        return hash(String.join("|", definition.currencyId(), definition.scope().type().name(), definition.scope().key(),
                Integer.toString(definition.fractionalDigits()), definition.startingBalance().toPlainString(),
                definition.minimumBalance().toPlainString(), definition.maximumBalance().toPlainString(),
                Boolean.toString(definition.allowNegative()), definition.rounding().name(),
                Boolean.toString(definition.paymentsDefaultEnabled()), definition.paymentMinimum().toPlainString(),
                definition.paymentMaximum().toPlainString(), definition.confirmationThreshold().toPlainString(),
                definition.dailySendLimit().toPlainString(), definition.dailyReceiveLimit().toPlainString(),
                Long.toString(definition.paymentCooldown().toMillis())));
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String bounded(String value, int maximum, String field, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, field + " must not be blank");
        }
        if (normalized.length() > maximum) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, field + " exceeds " + maximum + " characters");
        }
        return normalized;
    }

    static String trim(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    static Identity identity(EconomyBalanceEntity balance) {
        return new Identity(balance.getPlayerId(), UUID.fromString(balance.getPlayerUuid()), balance.getPlayerName());
    }

    static Account snapshot(EconomyBalanceEntity balance, EconomyPlayerSettingsEntity settings) {
        return new Account(balance.getId(), identity(balance), balance.getCurrencyId(), balance.getScopeKey(),
                balance.getBalance(), balance.getVersion(), settings.getVersion(), settings.isPaymentsEnabled(),
                AccountStatus.valueOf(settings.getAccountStatus()));
    }

    static MutationOutcome outcome(EconomyResultStatus status, String operationId, BigDecimal balance,
                                   BigDecimal counterpartBalance, String message, Account account, Account counterpart) {
        return new MutationOutcome(status, operationId == null ? null : UUID.fromString(operationId), balance,
                counterpartBalance, message == null ? "" : message, account, counterpart);
    }

    private static BigDecimal normalize(BigDecimal amount, EconomySettings.Currency currency) {
        if (amount == null) throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount is required");
        long integerDigits = (long) amount.precision() - amount.scale();
        if (amount.scale() > DATABASE_SCALE || amount.signum() != 0 && integerDigits > 30L) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount exceeds DECIMAL(38,8) storage precision");
        }
        if (amount.scale() > currency.display().fractionalDigits()) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT,
                    "Amount exceeds the configured currency precision");
        }
        try {
            // Monetary callers must send the exact amount they intend to authorize. Rounding an
            // incoming request can turn a requested debit of 1.005 into a committed debit of
            // 1.01, which is unacceptable for native and gateway real-value operations.
            return amount.setScale(currency.display().fractionalDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new EconomyRejectedException(EconomyResultStatus.INVALID_AMOUNT, "Amount has invalid precision");
        }
    }
}
