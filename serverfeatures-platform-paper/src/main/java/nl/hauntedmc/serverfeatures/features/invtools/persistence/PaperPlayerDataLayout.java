package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.framework.playerdata.PlayerDataFiles;

import java.nio.file.Path;

/**
 * InvTools compatibility wrapper around the shared Paper playerdata layout resolver.
 */
final class PaperPlayerDataLayout {

    private PaperPlayerDataLayout() {
    }

    static Path playerDataDirectory(Path levelDirectory) {
        return PlayerDataFiles.dataDirectory(levelDirectory);
    }
}
