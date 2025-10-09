package nl.hauntedmc.serverfeatures.api.util;

/**
 * Immutable time quantity expressed in Minecraft ticks (20 ticks = 1 second).
 * Use factories like Time.seconds(10), Time.milliseconds(250), or Time.ticks(1).
 * Conversion:
 * - milliseconds -> ticks uses ceiling ((ms + 49) / 50) so short delays aren't lost.
 */
public final class BukkitTime {
    private final long ticks;

    private BukkitTime(long ticks) {
        this.ticks = Math.max(0L, ticks);
    }

    /** Create a Time from raw ticks. */
    public static BukkitTime ticks(long ticks) {
        return new BukkitTime(ticks);
    }

    /** Create a Time from milliseconds (ceil to the next tick). */
    public static BukkitTime milliseconds(long millis) {
        if (millis <= 0L) return new BukkitTime(0L);
        long ticks = (millis + 49L) / 50L; // ceil(ms / 50)
        return new BukkitTime(ticks);
    }

    /** Create a Time from whole seconds. */
    public static BukkitTime seconds(long seconds) {
        if (seconds <= 0L) return new BukkitTime(0L);
        return new BukkitTime(Math.multiplyExact(seconds, 20L));
    }

    /** Create a Time from fractional seconds (ceil to the next millisecond). */
    public static BukkitTime seconds(double seconds) {
        if (seconds <= 0.0) return new BukkitTime(0L);
        long millis = (long) Math.ceil(seconds * 1000.0);
        return milliseconds(millis);
    }

    /** Create a Time from minutes. */
    public static BukkitTime minutes(long minutes) {
        return seconds(Math.multiplyExact(minutes, 60L));
    }

    /** Create a Time from hours. */
    public static BukkitTime hours(long hours) {
        return seconds(Math.multiplyExact(hours, 3600L));
    }

    /** Number of ticks represented by this Time. */
    public long toTicks() {
        return ticks;
    }

    /** Add two Time values (throws on overflow). */
    public BukkitTime plus(BukkitTime other) {
        return new BukkitTime(Math.addExact(this.ticks, other.ticks));
    }

    /** Multiply a Time by a factor (throws on overflow). */
    public BukkitTime multipliedBy(long factor) {
        return new BukkitTime(Math.multiplyExact(this.ticks, factor));
    }

    @Override public String toString() {
        return ticks + "t";
    }
}
