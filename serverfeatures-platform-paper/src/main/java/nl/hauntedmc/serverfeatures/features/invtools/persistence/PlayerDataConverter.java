package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;

import java.io.IOException;

@FunctionalInterface
public interface PlayerDataConverter {

    ReadWriteNBT convertToCurrent(
            ReadWriteNBT source,
            int sourceVersion,
            int targetVersion
    ) throws IOException;
}
