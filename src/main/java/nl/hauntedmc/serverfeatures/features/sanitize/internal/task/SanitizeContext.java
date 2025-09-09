package nl.hauntedmc.serverfeatures.features.sanitize.internal.task;

import java.nio.file.Path;

public record SanitizeContext(
        Path serverRoot,
        String minecraftVersion
) { }
