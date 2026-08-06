package nl.hauntedmc.serverfeatures.features.economy.persistence;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyRepositoryRetryTest {

    @Test
    void recognizesMySqlConcurrencyFailuresWithoutDependingOnDriverMessages() {
        assertTrue(EconomyRepository.isTransient(wrappedSqlFailure(1_205, "HY000")));
        assertTrue(EconomyRepository.isTransient(wrappedSqlFailure(1_213, "40001")));
        assertTrue(EconomyRepository.isTransient(wrappedSqlFailure(1_062, "23000")));
        assertTrue(EconomyRepository.isTransient(wrappedSqlFailure(0, "40001")));
    }

    @Test
    void doesNotRetryPermanentIntegrityOrConnectionFailures() {
        assertFalse(EconomyRepository.isTransient(wrappedSqlFailure(1_452, "23000")));
        assertFalse(EconomyRepository.isTransient(wrappedSqlFailure(0, "08006")));
    }

    @Test
    void inspectsChainedSqlExceptions() {
        SQLException primary = new SQLException("statement failed", "HY000", 0);
        primary.setNextException(new SQLException("duplicate", "23000", 1_062));

        assertTrue(EconomyRepository.isTransient(primary));
    }

    private static RuntimeException wrappedSqlFailure(int errorCode, String sqlState) {
        return new IllegalStateException(
                "ORM operation failed",
                new SQLException("localized database error", sqlState, errorCode)
        );
    }
}
