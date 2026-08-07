package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** Builds stable semantic fingerprints used to reject conflicting idempotency replays. */
final class EconomyRequestFingerprint {
    private EconomyRequestFingerprint() {
    }

    static String mutation(TransactionType operationType, TransactionType journalType, Identity identity,
                           EconomySettings.Currency currency, BigDecimal amount, Long actorPlayerId,
                           String actorName, String reason, Map<String, String> metadata, boolean bypassFreeze) {
        return fingerprint("mutation-v2", operationType.name(), journalType.name(),
                EconomyPersistenceValues.accountId(identity.playerId(), currency.id(), currency.scope().key()),
                identity.playerUuid().toString(), currency.id(), currency.scope().key(), amount.toPlainString(),
                actorPlayerId == null ? "" : actorPlayerId.toString(), normalizedActor(actorName),
                reason == null ? "" : reason.trim(), canonicalMetadata(metadata), Boolean.toString(bypassFreeze));
    }

    static String transfer(Identity sender, Identity recipient, EconomySettings.Currency currency, BigDecimal amount,
                           Long actorPlayerId, String actorName, String reason, Map<String, String> metadata,
                           boolean bypassPaymentsToggle, boolean bypassFreeze) {
        return fingerprint("transfer-v2",
                EconomyPersistenceValues.accountId(sender.playerId(), currency.id(), currency.scope().key()), sender.playerUuid().toString(),
                EconomyPersistenceValues.accountId(recipient.playerId(), currency.id(), currency.scope().key()), recipient.playerUuid().toString(),
                currency.id(), currency.scope().key(), amount.toPlainString(), actorPlayerId == null ? "" : actorPlayerId.toString(),
                normalizedActor(actorName), reason == null ? "" : reason.trim(), canonicalMetadata(metadata),
                Boolean.toString(bypassPaymentsToggle), Boolean.toString(bypassFreeze));
    }

    static String accountSetting(TransactionType type, Identity identity, EconomySettings.Currency currency,
                                 Long actorPlayerId, String actorName, String reason, Map<String, String> metadata) {
        return fingerprint("account-setting-v1", type.name(),
                EconomyPersistenceValues.accountId(identity.playerId(), currency.id(), currency.scope().key()),
                identity.playerUuid().toString(), currency.id(), currency.scope().key(),
                actorPlayerId == null ? "" : actorPlayerId.toString(), normalizedActor(actorName),
                reason == null ? "" : reason.trim(), canonicalMetadata(metadata));
    }

    static String fingerprint(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) append(builder, part == null ? "" : part);
        return EconomyPersistenceValues.hash(builder.toString());
    }

    static String normalizedActor(String actorName) {
        return actorName == null || actorName.isBlank() ? "system" : actorName.trim();
    }

    private static String canonicalMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(metadata).forEach((key, value) -> {
            append(builder, key == null ? "" : key);
            append(builder, value == null ? "" : value);
        });
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        builder.append(part.length()).append(':').append(part).append(';');
    }
}
