package nl.hauntedmc.serverfeatures.framework.time;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Plugin-scoped monotonic clock that advances only while this backend process is online.
 *
 * <p>The checkpoint intentionally excludes unflushed crash time. Consumers can therefore live a few
 * seconds longer after a hard crash, but can never expire early because wall-clock time advanced while
 * the backend was offline.</p>
 */
public final class ServerActiveClock implements AutoCloseable {
    private static final long CHECKPOINT_PERIOD_TICKS = 100L;
    private static final int CHECKPOINT_BYTES = Long.BYTES;

    private final Plugin plugin;
    private final Path checkpointPath;
    private final AtomicLong accumulatedMillis = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile long sessionStartedNanos;
    private volatile BukkitTask checkpointTask;

    public ServerActiveClock(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.checkpointPath = plugin.getDataFolder().toPath()
                .resolve("runtime")
                .resolve("server-active-clock.bin");
    }

    public synchronized void start() {
        if (sessionStartedNanos != 0L) {
            return;
        }
        accumulatedMillis.set(readCheckpoint());
        sessionStartedNanos = System.nanoTime();
        checkpointTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkpointSafely,
                CHECKPOINT_PERIOD_TICKS,
                CHECKPOINT_PERIOD_TICKS
        );
    }

    public synchronized long nowMillis() {
        long started = sessionStartedNanos;
        if (started == 0L) {
            return accumulatedMillis.get();
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - started);
        return Math.addExact(accumulatedMillis.get(), elapsedNanos / 1_000_000L);
    }

    public synchronized void checkpoint() throws IOException {
        if (closed.get()) {
            return;
        }
        long current = nowMillis();
        accumulatedMillis.set(current);
        sessionStartedNanos = System.nanoTime();
        writeCheckpoint(current);
    }

    private long readCheckpoint() {
        try {
            if (!Files.isRegularFile(checkpointPath)) {
                return 0L;
            }
            byte[] bytes = Files.readAllBytes(checkpointPath);
            if (bytes.length != CHECKPOINT_BYTES) {
                throw new IOException("Unexpected active-clock checkpoint length: " + bytes.length);
            }
            return Math.max(0L, ByteBuffer.wrap(bytes).getLong());
        } catch (IOException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not read the server active-clock checkpoint; starting at zero.",
                    exception
            );
            return 0L;
        }
    }

    private void writeCheckpoint(long value) throws IOException {
        Files.createDirectories(checkpointPath.getParent());
        Path temporary = checkpointPath.resolveSibling(checkpointPath.getFileName() + ".tmp");
        byte[] bytes = ByteBuffer.allocate(CHECKPOINT_BYTES).putLong(value).array();
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        try {
            Files.move(
                    temporary,
                    checkpointPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, checkpointPath, StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(checkpointPath.getParent());
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems do not expose directory fsync through FileChannel.
        }
    }

    private void checkpointSafely() {
        try {
            checkpoint();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not checkpoint the server active clock.", exception);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        BukkitTask task = checkpointTask;
        checkpointTask = null;
        if (task != null) {
            task.cancel();
        }
        long current = nowMillis();
        accumulatedMillis.set(current);
        sessionStartedNanos = 0L;
        try {
            writeCheckpoint(current);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not write the final server active-clock checkpoint.", exception);
        }
    }
}
