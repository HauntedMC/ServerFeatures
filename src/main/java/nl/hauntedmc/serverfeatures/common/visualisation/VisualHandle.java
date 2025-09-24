package nl.hauntedmc.serverfeatures.common.visualisation;

/**
 * Lifecycle handle for a visualisation instance.
 * Always call {@link #clear()} when done to remove entities/particles.
 */
public interface VisualHandle {
    /** Remove all rendered elements (idempotent). */
    void clear();

    /** @return true if already cleared. */
    boolean isCleared();
}
