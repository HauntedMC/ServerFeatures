package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

/** Enforces account freeze, payment cooldown, and daily transfer limits within the transfer transaction. */
final class EconomyPaymentPolicy {
    void requireActive(EconomyPlayerSettingsEntity settings, boolean bypassFreeze) {
        if (!bypassFreeze && AccountStatus.FROZEN.name().equals(settings.getAccountStatus())) {
            throw new EconomyRejectedException(EconomyResultStatus.ACCOUNT_FROZEN, "Account is frozen");
        }
    }

    void enforceCooldown(EconomyPlayerSettingsEntity sender, EconomySettings.Currency currency, long now) {
        long cooldown = currency.payments().cooldown().toMillis();
        if (cooldown <= 0L) return;
        Long previous = sender.getLastPaymentAt();
        if (previous != null) {
            long remaining = cooldown - Math.max(0L, now - previous);
            if (remaining > 0L) {
                throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED,
                        "Payment cooldown active for " + remaining + " ms");
            }
        }
        sender.setLastPaymentAt(now);
        sender.setUpdatedAt(now);
    }

    void applyDailyLimits(Session session, EconomyBalanceEntity sender, EconomyBalanceEntity recipient,
                          EconomySettings.Currency currency, BigDecimal amount, long now) {
        BigDecimal sendLimit = currency.payments().dailySendLimit();
        BigDecimal receiveLimit = currency.payments().dailyReceiveLimit();
        if (sendLimit.signum() <= 0 && receiveLimit.signum() <= 0) return;
        String date = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString();
        EconomyDailyUsageEntity senderUsage;
        EconomyDailyUsageEntity recipientUsage;
        // Lock usage rows by deterministic account id to avoid reciprocal-transfer deadlocks.
        if (sender.getId().compareTo(recipient.getId()) < 0) {
            senderUsage = usage(session, sender, date, now);
            recipientUsage = usage(session, recipient, date, now);
        } else {
            recipientUsage = usage(session, recipient, date, now);
            senderUsage = usage(session, sender, date, now);
        }
        BigDecimal sent = senderUsage.getSentAmount().add(amount);
        if (sendLimit.signum() > 0 && sent.compareTo(sendLimit) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Daily send limit exceeded");
        }
        BigDecimal received = recipientUsage.getReceivedAmount().add(amount);
        if (receiveLimit.signum() > 0 && received.compareTo(receiveLimit) > 0) {
            throw new EconomyRejectedException(EconomyResultStatus.LIMIT_EXCEEDED, "Daily receive limit exceeded");
        }
        senderUsage.setSentAmount(EconomyPersistenceValues.databaseAmount(sent));
        senderUsage.setSentCount(Math.addExact(senderUsage.getSentCount(), 1));
        senderUsage.setUpdatedAt(now);
        recipientUsage.setReceivedAmount(EconomyPersistenceValues.databaseAmount(received));
        recipientUsage.setUpdatedAt(now);
    }

    private EconomyDailyUsageEntity usage(Session session, EconomyBalanceEntity account, String date, long now) {
        String id = account.getId() + ":" + date;
        EconomyDailyUsageEntity usage = session.find(EconomyDailyUsageEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (usage != null) return usage;
        usage = new EconomyDailyUsageEntity();
        usage.setId(id);
        usage.setAccountId(account.getId());
        usage.setUsageDate(date);
        usage.setSentAmount(EconomyPersistenceValues.databaseAmount(BigDecimal.ZERO));
        usage.setReceivedAmount(EconomyPersistenceValues.databaseAmount(BigDecimal.ZERO));
        usage.setSentCount(0);
        usage.setUpdatedAt(now);
        session.persist(usage);
        session.flush();
        return usage;
    }
}
