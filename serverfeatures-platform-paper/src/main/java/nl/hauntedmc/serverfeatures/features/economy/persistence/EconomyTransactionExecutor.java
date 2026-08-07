package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.Session;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.function.Supplier;

/** Executes retryable ORM work and supplies the database-authoritative transaction clock. */
final class EconomyTransactionExecutor {
    private static final int MAX_RETRIES = 3;
    private static final String DATABASE_TIME_QUERY =
            "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";

    <T> T execute(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return work.get();
            } catch (EconomyRejectedException rejected) {
                throw rejected;
            } catch (RuntimeException failure) {
                last = failure;
                if (!isTransient(failure) || attempt == MAX_RETRIES) throw failure;
                try {
                    Thread.sleep(5L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
        throw last == null ? new IllegalStateException("Economy operation did not execute") : last;
    }

    static long databaseNow(Session session) {
        return session.doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DATABASE_TIME_QUERY);
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("MySQL returned no authoritative Economy timestamp");
                return result.getLong(1);
            }
        });
    }

    static boolean isTransient(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof LockTimeoutException || current instanceof OptimisticLockException
                    || current instanceof PessimisticLockException) return true;
            if (current instanceof SQLException sqlException && isTransientSql(sqlException)) return true;
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("deadlock") || normalized.contains("lock wait timeout")
                        || normalized.contains("duplicate entry")
                        || normalized.contains("constraint") && normalized.contains("idempotency")) return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTransientSql(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            // MySQL lock/deadlock/duplicate races plus standard serialization and connection failures.
            if (current.getErrorCode() == 1_205 || current.getErrorCode() == 1_213 || current.getErrorCode() == 1_062
                    || "40001".equals(state) || state != null && state.startsWith("08")) return true;
        }
        return false;
    }

    /** Lazily reads one authoritative timestamp for provisioning work in a transaction. */
    static final class Clock {
        private final Session session;
        private Long timestamp;

        Clock(Session session) {
            this.session = session;
        }

        long now() {
            if (timestamp == null) timestamp = databaseNow(session);
            return timestamp;
        }
    }
}
