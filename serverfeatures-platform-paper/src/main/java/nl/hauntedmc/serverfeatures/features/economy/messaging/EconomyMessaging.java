package nl.hauntedmc.serverfeatures.features.economy.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns Economy Redis publish/subscribe lifecycle. */
public final class EconomyMessaging {
    private static final long UNSUBSCRIBE_TIMEOUT_SECONDS = 5L;

    private final Economy feature;
    private final MessagingDataAccess messaging;
    private final String channel;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Subscription subscription;

    public EconomyMessaging(Economy feature, MessagingDataAccess messaging, String channel) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public synchronized void start() {
        if (closed.get()) {
            throw new IllegalStateException("Economy messaging is closed");
        }
        if (subscription != null) {
            return;
        }
        subscription = Objects.requireNonNull(
                messaging.subscribe(
                        channel,
                        EconomyBalanceMessage.TYPE,
                        EconomyBalanceMessage.class,
                        message -> feature.service().applyRemoteBalance(message)
                ),
                "Redis subscribe returned no subscription"
        );
    }

    public void publish(String operationId, Account account) {
        if (closed.get() || account == null) {
            return;
        }
        EconomyBalanceMessage message = new EconomyBalanceMessage(
                feature.settings().serverKey(),
                operationId,
                account.identity().playerId(),
                account.identity().playerUuid().toString(),
                account.identity().playerName(),
                account.currencyId(),
                account.scopeKey(),
                account.balance(),
                account.version(),
                System.currentTimeMillis()
        );
        messaging.publish(channel, message).exceptionally(failure -> {
            feature.getLogger().warning("Could not publish Economy balance update: " + rootMessage(failure));
            return null;
        });
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Subscription current;
        synchronized (this) {
            current = subscription;
            subscription = null;
        }
        if (current == null) {
            return;
        }
        try {
            CompletableFuture<Void> future = current.unsubscribe();
            if (future != null) {
                future.orTimeout(UNSUBSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(failure -> {
                            feature.getLogger().warning(
                                    "Could not confirm Economy subscription shutdown: " + rootMessage(failure)
                            );
                            return null;
                        });
            }
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not close Economy subscription: " + rootMessage(exception));
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
