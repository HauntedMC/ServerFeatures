package nl.hauntedmc.serverfeatures.framework.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delegates to a standard java.util.Logger,
 * but prefixes every message with [featureName].
 */
public class FeatureLogger {
    private final Logger delegate;
    private final String prefix;

    public FeatureLogger(Logger delegate, String featureName) {
        this.delegate = delegate;
        this.prefix   = "[" + featureName + "] ";
    }

    public void info(String msg) {
        delegate.info(prefix + msg);
    }

    public void warning(String msg) {
        delegate.warning(prefix + msg);
    }

    public void severe(String msg) {
        delegate.severe(prefix + msg);
    }

    public void fine(String msg) {
        delegate.fine(prefix + msg);
    }

    public void log(Level level, String msg) {
        delegate.log(level, prefix + msg);
    }
}
