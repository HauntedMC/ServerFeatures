package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Produces globally comparable, process-monotonic request revisions.
 *
 * <p>Wall-clock microseconds dominate the value. A small random process suffix makes equal-microsecond
 * requests from different backends deterministic, while the local atomic clock guarantees strict
 * ordering for rapid commands in one process. Loaded database revisions are observed before issuing
 * another write, so moderate clock skew cannot make a player's next command stale.</p>
 */
final class AutoPickupWriteRevisionClock {

    private static final int PROCESS_SUFFIX_RANGE = 256;

    private final int processSuffix;
    private final LongSupplier epochMicros;
    private final AtomicLong lastRevision = new AtomicLong();

    AutoPickupWriteRevisionClock() {
        this(ThreadLocalRandom.current().nextInt(PROCESS_SUFFIX_RANGE), AutoPickupWriteRevisionClock::nowMicros);
    }

    AutoPickupWriteRevisionClock(int processSuffix, LongSupplier epochMicros) {
        if (processSuffix < 0 || processSuffix >= PROCESS_SUFFIX_RANGE) {
            throw new IllegalArgumentException("processSuffix must be between 0 and 255");
        }
        this.processSuffix = processSuffix;
        this.epochMicros = epochMicros;
    }

    long next() {
        long micros = epochMicros.getAsLong();
        long wallRevision;
        try {
            wallRevision = Math.addExact(
                    Math.multiplyExact(micros, PROCESS_SUFFIX_RANGE),
                    processSuffix
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("AutoPickup write revision overflow", exception);
        }

        return lastRevision.updateAndGet(previous -> {
            if (previous == Long.MAX_VALUE) {
                throw new IllegalStateException("AutoPickup write revision space exhausted");
            }
            return Math.max(previous + 1L, wallRevision);
        });
    }

    void observe(long revision) {
        if (revision > 0L) {
            lastRevision.accumulateAndGet(revision, Math::max);
        }
    }

    private static long nowMicros() {
        Instant now = Instant.now();
        try {
            return Math.addExact(
                    Math.multiplyExact(now.getEpochSecond(), 1_000_000L),
                    now.getNano() / 1_000L
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Current time cannot be represented as microseconds", exception);
        }
    }
}
