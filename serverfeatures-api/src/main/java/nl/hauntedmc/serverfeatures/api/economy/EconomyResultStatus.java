package nl.hauntedmc.serverfeatures.api.economy;

/** Structured result status for native economy operations. */
public enum EconomyResultStatus {
    SUCCESS,
    IDEMPOTENT_REPLAY,
    IDEMPOTENCY_CONFLICT,
    INSUFFICIENT_FUNDS,
    ACCOUNT_FROZEN,
    PAYMENTS_DISABLED,
    LIMIT_EXCEEDED,
    UNKNOWN_CURRENCY,
    UNKNOWN_PLAYER,
    INVALID_AMOUNT,
    TEMPORARY_FAILURE
}
