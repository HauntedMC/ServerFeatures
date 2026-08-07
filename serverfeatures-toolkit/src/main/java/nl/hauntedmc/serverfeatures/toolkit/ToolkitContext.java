package nl.hauntedmc.serverfeatures.toolkit;

import org.slf4j.Logger;

import java.nio.file.Path;

/** Minimal host contract required by toolkit configuration and resource services. */
public interface ToolkitContext {
    Path getDataDirectory();
    Logger getLogger();

    default ClassLoader getResourceClassLoader() {
        return getClass().getClassLoader();
    }
}
